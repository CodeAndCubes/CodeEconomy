package com.mrleonardos.codeeconomy.internal.adapter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codeeconomy.api.EconomyLimits;
import com.mrleonardos.codeeconomy.api.EconomyService;
import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.BalanceEntry;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.api.model.ResultCode;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;
import com.mrleonardos.codeeconomy.api.model.TransferRequest;
import com.mrleonardos.codeeconomy.api.model.TransferResult;
import com.mrleonardos.codeeconomy.internal.EconomySection;
import com.mrleonardos.codeeconomy.internal.service.PlayerLookup;

/**
 * Деньги ForgeEssentials под интерфейсом {@link EconomyService}.
 *
 * <p>
 * Чужой мод держит один кошелёк на игрока со счётом в целых единицах, без валют, журнала и ключа
 * идемпотентности. Поэтому заявка объявляет только {@code balance} и {@code transfer}, а история и топ
 * отвечают пустым списком: команды, которые на них держатся, при таком владельце роли не
 * регистрируются вовсе, и заглушки с исключением из середины метода тут нет.
 *
 * <p>
 * Перевод собирается из {@code withdraw} у одного кошелька и {@code add} у другого. Между этими двумя
 * вызовами нет ни атомарности, ни отката, и снаружи это не чинится: падение сервера между ними теряет
 * деньги. Об этом уходит строка в лог при выборе владельца.
 */
final class ForgeEssentialsEconomy implements EconomyService {

    private final ForgeEssentialsApi api;
    private final Supplier<EconomySection> section;
    private final PlayerLookup lookup;
    private final Scheduler scheduler;

    ForgeEssentialsEconomy(ForgeEssentialsApi api, Supplier<EconomySection> section, PlayerLookup lookup,
        Scheduler scheduler) {
        this.api = api;
        this.section = section;
        this.lookup = lookup;
        this.scheduler = scheduler;
    }

    @Override
    public List<CurrencyRecord> currencies() {
        return Collections.singletonList(currency());
    }

    @Override
    public Optional<CurrencyRecord> currency(String currencyId) {
        return ours(currencyId) ? Optional.of(currency()) : Optional.empty();
    }

    @Override
    public String defaultCurrencyId() {
        return currencyId();
    }

    @Override
    public long balance(UUID player, String currencyId) {
        Object wallet = walletOf(player, currencyId);
        return wallet == null ? 0L : api.balance(wallet);
    }

    @Override
    public boolean has(UUID player, long amount, String currencyId) {
        Object wallet = walletOf(player, currencyId);
        return wallet != null && api.covers(wallet, amount);
    }

    @Override
    public Optional<AccountView> account(UUID player) {
        Object wallet = walletOf(player, currencyId());
        if (wallet == null) {
            return Optional.empty();
        }
        Map<String, Long> balances = new LinkedHashMap<>();
        balances.put(currencyId(), Long.valueOf(api.balance(wallet)));
        return Optional.of(AccountView.of(player, lookup.name(player), balances, false, 0L));
    }

    @Override
    public TransferResult transfer(TransferRequest request) {
        return execute(request);
    }

    @Override
    public TransferResult deposit(TransferRequest request) {
        return execute(request);
    }

    @Override
    public TransferResult withdraw(TransferRequest request) {
        return execute(request);
    }

    @Override
    public TransferResult set(TransferRequest request) {
        return execute(request);
    }

    @Override
    public TransferResult reset(TransferRequest request) {
        return execute(request);
    }

    @Override
    public CompletableFuture<TransferResult> submit(TransferRequest request) {
        CompletableFuture<TransferResult> done = new CompletableFuture<>();
        scheduler.onMainThread(() -> {
            try {
                done.complete(execute(request));
            } catch (RuntimeException failure) {
                done.completeExceptionally(failure);
            }
        });
        return done;
    }

    /** Журнала у чужого мода нет, поэтому истории нет ни у кого: умение {@code history} не объявлено. */
    @Override
    public List<TransactionRecord> history(UUID player, int page, int pageSize) {
        return Collections.emptyList();
    }

    /** Перебрать все кошельки чужой мод не даёт: умение {@code top} не объявлено. */
    @Override
    public List<BalanceEntry> top(String currencyId, int page, int pageSize) {
        return Collections.emptyList();
    }

    private TransferResult execute(TransferRequest request) {
        if (!ours(request.currencyId())) {
            return TransferResult.failure(ResultCode.UNKNOWN_CURRENCY, request.transactionId());
        }
        ResultCode refused = amountCode(request);
        if (refused != null) {
            return TransferResult.failure(refused, request.transactionId());
        }
        switch (request.kind()) {
            case TRANSFER:
                return move(request);
            case DEPOSIT:
                return change(request, request.amount());
            case WITHDRAW:
                return take(request);
            case SET:
                return exact(request, request.amount());
            case RESET:
                return exact(request, currency().startBalance());
            default:
                return TransferResult.failure(ResultCode.INVALID_REQUEST, request.transactionId());
        }
    }

    private TransferResult move(TransferRequest request) {
        UUID from = request.from()
            .get();
        UUID to = request.to()
            .get();
        if (from.equals(to)) {
            return TransferResult.failure(ResultCode.SAME_ACCOUNT, request.transactionId());
        }
        Object source = walletOf(from, request.currencyId());
        Object target = walletOf(to, request.currencyId());
        if (source == null || target == null) {
            return TransferResult.failure(ResultCode.STORE_FAILURE, request.transactionId());
        }
        if (!api.withdraw(source, request.amount())) {
            return TransferResult
                .failure(ResultCode.INSUFFICIENT, request.transactionId(), Long.valueOf(api.balance(source)), null);
        }
        api.add(target, request.amount());
        return TransferResult
            .success(request.transactionId(), Long.valueOf(api.balance(source)), Long.valueOf(api.balance(target)));
    }

    private TransferResult change(TransferRequest request, long amount) {
        Object wallet = walletOf(
            request.to()
                .get(),
            request.currencyId());
        if (wallet == null) {
            return TransferResult.failure(ResultCode.STORE_FAILURE, request.transactionId());
        }
        api.add(wallet, amount);
        return TransferResult.success(request.transactionId(), null, Long.valueOf(api.balance(wallet)));
    }

    private TransferResult take(TransferRequest request) {
        Object wallet = walletOf(
            request.from()
                .get(),
            request.currencyId());
        if (wallet == null) {
            return TransferResult.failure(ResultCode.STORE_FAILURE, request.transactionId());
        }
        if (!api.withdraw(wallet, request.amount())) {
            return TransferResult
                .failure(ResultCode.INSUFFICIENT, request.transactionId(), Long.valueOf(api.balance(wallet)), null);
        }
        return TransferResult.success(request.transactionId(), Long.valueOf(api.balance(wallet)), null);
    }

    private TransferResult exact(TransferRequest request, long amount) {
        Object wallet = walletOf(
            request.to()
                .get(),
            request.currencyId());
        if (wallet == null) {
            return TransferResult.failure(ResultCode.STORE_FAILURE, request.transactionId());
        }
        if (amount < 0L) {
            return TransferResult.failure(ResultCode.BELOW_FLOOR, request.transactionId());
        }
        api.set(wallet, amount);
        return TransferResult.success(request.transactionId(), null, Long.valueOf(api.balance(wallet)));
    }

    /**
     * Границы одного перевода из секции {@code [economy]} главного файла. Личный потолок из меты сюда не
     * доезжает: у чужого мода нет ни меты, ни места, где её спросить, и обещать его было бы неправдой.
     */
    private ResultCode amountCode(TransferRequest request) {
        if (request.kind() == TransactionRecord.Kind.RESET) {
            return request.amount() == 0L ? null : ResultCode.BAD_AMOUNT;
        }
        if (request.kind() != TransactionRecord.Kind.SET && request.amount() <= 0L) {
            return ResultCode.BAD_AMOUNT;
        }
        if (request.kind() != TransactionRecord.Kind.TRANSFER) {
            return null;
        }
        EconomySection limits = section.get();
        if (request.amount() < limits.minTransfer) {
            return ResultCode.BAD_AMOUNT;
        }
        return request.amount() > limits.maxTransfer ? ResultCode.ABOVE_CEILING : null;
    }

    private Object walletOf(UUID player, String currencyId) {
        return ours(currencyId) ? api.wallet(player, lookup.name(player)) : null;
    }

    private boolean ours(String currencyId) {
        return currencyId().equals(currencyId);
    }

    private String currencyId() {
        return section.get()
            .currencyId();
    }

    /**
     * Единственная валюта чужого мода: имя он называет сам, идентификатор и показ берутся из
     * {@code [economy] defaultCurrency}. Дробной части у него нет, поэтому минорная единица здесь равна
     * целой.
     */
    private CurrencyRecord currency() {
        String id = currencyId();
        return CurrencyRecord.builder(id)
            .displayName(api.currencyName(id))
            .symbol(api.currencyName(id))
            .decimals(0)
            .startBalance(0L)
            .minBalance(0L)
            .negativeFloor(0L)
            .maxBalance(EconomyLimits.MAX_BALANCE_CAP)
            .payAllowed(true)
            .visible(true)
            .build();
    }
}
