package com.mrleonardos.codeeconomy.internal.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codeeconomy.api.Amounts;
import com.mrleonardos.codeeconomy.api.EconomyLimits;
import com.mrleonardos.codeeconomy.api.event.DegradedEvent;
import com.mrleonardos.codeeconomy.api.event.RecoveryEvent;
import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.BalanceEntry;
import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.api.model.ResultCode;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;
import com.mrleonardos.codeeconomy.api.model.TransferRequest;
import com.mrleonardos.codeeconomy.api.model.TransferResult;
import com.mrleonardos.codeeconomy.api.store.ChangeBatch;
import com.mrleonardos.codeeconomy.api.store.CheckpointResult;
import com.mrleonardos.codeeconomy.api.store.EconomyStore;
import com.mrleonardos.codeeconomy.api.store.StoreMaintenance;
import com.mrleonardos.codeeconomy.api.store.StoreResult;
import com.mrleonardos.codeeconomy.api.store.StoreSnapshot;
import com.mrleonardos.codeeconomy.api.store.StoreVerification;
import com.mrleonardos.codeeconomy.internal.EconomyConfig;
import com.mrleonardos.codeeconomy.internal.EconomyNodes;
import com.mrleonardos.codeeconomy.internal.Lazy;
import com.mrleonardos.codeeconomy.internal.event.EventDispatcher;
import com.mrleonardos.codeeconomy.internal.guard.GuardChain;
import com.mrleonardos.codeeconomy.internal.service.PlayerLookup;
import com.mrleonardos.codeeconomy.internal.store.Currencies;
import com.mrleonardos.codeeconomy.internal.store.Recovery;

/**
 * Конвейер денег: один писатель, отказные пути до первой записи, журнал как истина.
 *
 * <p>
 * Порядок шагов фиксированный: разбор запроса, идемпотентность, стороны и заморозка, потолки и пол,
 * гварды, запись в хранилище, коммит снимка, события. Каждый отказ стоит до первой записи, поэтому
 * частичное списание невозможно по построению: перевод это одна строка журнала, а не два действия.
 *
 * <p>
 * Провайдер и цепочка гвардов берутся по первому обращению, а не в конструкторе: реестры
 * {@code EconomyApi} открыты всю фазу инициализации, и мод, загруженный после codeeconomy, обязан
 * успеть в них попасть. Всё, что провайдер умеет сверх записи, спрашивается у него самого через
 * {@link StoreMaintenance}: встроенного json движок по имени не знает.
 */
public final class Ledger {

    private final Lazy<EconomyStore> store;
    private final Lazy<GuardChain> guards;
    private final List<CurrencyRecord> currencyList;
    private final Map<String, CurrencyRecord> currencies;
    private final Recovery.StartBalances start;
    private final String defaultCurrencyId;
    private final EconomyLimits limits;
    private final long minTransfer;
    private final long maxTransfer;
    private final int historyEntries;
    private final long historyMillis;
    private final long idempotencyMillis;
    private final boolean logChanges;
    private final boolean logChecks;
    private final EventDispatcher events;
    private final PlayerLookup lookup;
    private final LongSupplier clock;
    private final Logger log;

    private final IdempotencyIndex index = new IdempotencyIndex();
    private final TopIndex topIndex;
    private volatile LedgerState state = LedgerState.empty();
    private volatile boolean readOnly;
    private volatile boolean degraded;

    public Ledger(Supplier<EconomyStore> store, List<CurrencyRecord> currencies, String defaultCurrencyId,
        EconomyConfig config, EconomyLimits limits, Supplier<GuardChain> guards, EventDispatcher events,
        PlayerLookup lookup, LongSupplier clock, Logger log) {
        this.store = Lazy.of(store);
        this.guards = Lazy.of(guards);
        this.currencyList = Collections.unmodifiableList(new ArrayList<>(currencies));
        this.currencies = Currencies.byId(currencies);
        this.start = Currencies.startBalances(currencies);
        this.defaultCurrencyId = defaultCurrencyId;
        this.limits = limits;
        this.minTransfer = Math.max(0L, config.minTransfer());
        this.maxTransfer = Math.max(0L, config.maxTransfer());
        this.historyEntries = limits.historyEntries();
        this.historyMillis = config.historyMillis();
        this.idempotencyMillis = config.idempotencyMillis();
        this.logChanges = config.logChanges();
        this.logChecks = config.logChecks();
        this.events = events;
        this.lookup = lookup;
        this.clock = clock;
        this.log = log;
        this.topIndex = new TopIndex(config.cacheTicks());
    }

    /** Поднять состояние из провайдера и сообщить слушателям итог восстановления. */
    public void loadWorld() {
        start(store().load());
    }

    /** Принять готовый снимок: вход для тестов и для провайдера, загруженного отдельно. */
    public void start(StoreSnapshot snapshot) {
        LedgerState loaded = LedgerState
            .of(snapshot.accounts(), snapshot.checkpointSeq(), snapshot.transactions(), historyEntries);
        state = loaded;
        readOnly = snapshot.readOnly();
        index.rebuild(snapshot.transactions());
        int replayed = 0;
        for (TransactionRecord record : snapshot.transactions()) {
            if (record.seq() > snapshot.checkpointSeq()) {
                replayed++;
            }
        }
        log.info(
            "Economy is served by provider {} with {} account(s), checkpoint {}, {} journal record(s) replayed, {} mismatch(es){}",
            store().id(),
            Integer.valueOf(
                loaded.accounts()
                    .size()),
            Long.valueOf(snapshot.checkpointSeq()),
            Integer.valueOf(replayed),
            Integer.valueOf(
                snapshot.findings()
                    .size()),
            snapshot.readOnly() ? ", the mod is read only: " + snapshot.reason()
                .orElse("") : "");
        events.recovery(RecoveryEvent.of(snapshot.checkpointSeq(), replayed, snapshot.findings()));
    }

    public LedgerState state() {
        return state;
    }

    public boolean readOnly() {
        return readOnly;
    }

    public boolean degraded() {
        return degraded;
    }

    public EventDispatcher events() {
        return events;
    }

    public GuardChain guards() {
        return guards.get();
    }

    public List<CurrencyRecord> currencies() {
        return currencyList;
    }

    public Optional<CurrencyRecord> currency(String currencyId) {
        return Optional.ofNullable(currencies.get(currencyId));
    }

    public String defaultCurrencyId() {
        return defaultCurrencyId;
    }

    public long balance(UUID player, String currencyId) {
        CurrencyRecord currency = currencies.get(currencyId);
        return currency == null ? 0L : balanceOf(state, player, currency);
    }

    public boolean has(UUID player, long amount, String currencyId) {
        return balance(player, currencyId) >= amount;
    }

    public List<TransactionRecord> history(UUID player, int page, int pageSize) {
        return state.history()
            .byPlayer(player, page, pageSize);
    }

    public List<BalanceEntry> top(String currencyId, int page, int pageSize, long tick) {
        CurrencyRecord currency = currencies.get(currencyId);
        if (currency == null) {
            return Collections.emptyList();
        }
        return topIndex.top(currency.id(), currency.startBalance(), state.accounts(), page, pageSize, tick);
    }

    /**
     * Провести операцию через конвейер. Вызывается только в главном потоке, за этим следит
     * {@code LedgerService}. Отказ носителя отвечает {@code STORE_FAILURE}, остальное либо код отказа,
     * либо исключение вызывающему: подмена ответа на хранилище скрыла бы настоящую причину.
     *
     * <p>
     * Пол баланса решает нода {@code codeeconomy.bypass.minbalance} у автора операции. Операция без
     * автора идёт по обычному полу: пустой {@code actor} ставит кто угодно, и раздавать по нему право
     * уводить чужие счета в минус нельзя.
     */
    public TransferResult execute(TransferRequest request, ChangeCause cause) {
        return pipeline(request, cause, null);
    }

    /**
     * Та же операция с уже решённым обходом пола.
     *
     * <p>
     * Вход для команд: право отправителя там спрашивает шов команд через {@code PermissionService}
     * ядра, и для консоли, у которой нет uuid, это единственный способ ответить честно.
     */
    public TransferResult execute(TransferRequest request, ChangeCause cause, boolean floorBypass) {
        return pipeline(request, cause, Boolean.valueOf(floorBypass));
    }

    /**
     * Сверка состояния с носителем, безопасна в фоновом потоке: работает по снимку. Провайдер с
     * обслуживанием сверяет по своему носителю от чекпоинта, остальные отдают построчную сверку
     * кольцевой истории, а итог сравнивается только когда история полная.
     */
    public StoreVerification verifyDetailed() {
        LedgerState current = state;
        StoreMaintenance keeper = maintenance();
        if (keeper != null) {
            return keeper.verify(current.accounts(), current.lastSeq());
        }
        boolean complete = current.checkpointSeq() == 0L && current.history()
            .size() < historyEntries
            && (historyMillis <= 0L || current.history()
                .oldestTs() >= clock.getAsLong() - historyMillis);
        if (!complete) {
            return StoreVerification.voided(
                "provider " + store().id()
                    + " offers no journal to check and the in-memory history is partial, nothing to compare against");
        }
        List<String> findings = Recovery.verify(
            current.accounts(),
            current.history()
                .records(),
            start,
            true);
        return StoreVerification.of(findings, 0L, 0L, 0L);
    }

    /** Принудительный снимок: чекпоинт записывается сейчас, журнал не трогается. */
    public CheckpointResult checkpoint() {
        if (readOnly) {
            return CheckpointResult
                .failure(StoreResult.failure(StoreResult.Failure.WRITE_FAILED, "the mod is read only"));
        }
        LedgerState current = state;
        StoreMaintenance keeper = maintenance();
        if (keeper == null) {
            StoreResult stored = save(current);
            return stored.successful() ? CheckpointResult.written(current.lastSeq()) : CheckpointResult.failure(stored);
        }
        CheckpointResult written = keeper.checkpoint(snapshotOf(current));
        if (written.successful()) {
            state = current.withCheckpoint(written.seq());
        }
        return written;
    }

    /** Свежий чекпоинт и обрезка журнала. */
    public StoreResult compact() {
        if (readOnly) {
            return StoreResult.failure(StoreResult.Failure.WRITE_FAILED, "the mod is read only");
        }
        return save(state);
    }

    /**
     * Снять признак карантина и открыть мутации.
     *
     * <p>
     * Признак стоит в состоянии мира и переживает перезапуск, поэтому снять его может только человек,
     * разобравшийся с потерянными записями.
     */
    public StoreResult liftReadOnly() {
        StoreMaintenance keeper = maintenance();
        if (keeper == null) {
            return StoreResult
                .failure(StoreResult.Failure.UNSUPPORTED, "provider " + store().id() + " keeps no quarantine mark");
        }
        StoreResult lifted = keeper.liftReadOnly();
        if (lifted.successful()) {
            readOnly = false;
            log.warn("The read only mode is lifted by an administrator, mutations are open again");
        }
        return lifted;
    }

    /** Отметить последний известный ник на существующем счёте: журнал не пишется, счёт не создаётся. */
    public StoreResult markName(UUID player, String name) {
        if (name == null || name.isEmpty()) {
            return StoreResult.success();
        }
        return amend(
            player,
            account -> AccountView.of(account.uuid(), name, account.balances(), account.frozen(), account.createdAt()),
            false);
    }

    /** Заморозить или разморозить существующий счёт: движение закрыто в обе стороны. */
    public StoreResult setFrozen(UUID player, boolean frozen) {
        return amend(
            player,
            account -> AccountView.of(
                account.uuid(),
                account.name()
                    .orElse(null),
                account.balances(),
                frozen,
                account.createdAt()),
            true);
    }

    /**
     * Правка счёта без движения денег.
     *
     * @param durable писать ли чекпоинт немедленно. Заморозка обязана пережить падение процесса, и это
     *                редкая команда администратора. Отметка ника идёт на каждом входе игрока, ей
     *                хватает ближайшего автосейва: полная перезапись файла на каждый вход это лаг тика
     *                за то, что и так восстановится по журналу. Провайдеру без обслуживания просить
     *                нечего: его {@code apply} уже зафиксировал правку.
     */
    private StoreResult amend(UUID player, UnaryOperator<AccountView> change, boolean durable) {
        if (readOnly) {
            return StoreResult.failure(StoreResult.Failure.WRITE_FAILED, "the mod is read only");
        }
        LedgerState current = state;
        AccountView account = current.accounts()
            .get(player);
        if (account == null) {
            return StoreResult.success();
        }
        AccountView updated = change.apply(account);
        if (updated.equals(account)) {
            return StoreResult.success();
        }
        ChangeBatch batch = ChangeBatch.builder(ChangeCause.API)
            .upsert(updated)
            .build();
        StoreResult stored = write(batch);
        if (!stored.successful()) {
            degrade(stored);
            return stored;
        }
        restore();
        Map<UUID, AccountView> accounts = new LinkedHashMap<>(current.accounts());
        accounts.put(player, updated);
        state = current.withAccounts(accounts);
        if (durable && maintenance() != null) {
            CheckpointResult written = checkpoint();
            if (!written.successful()) {
                return written.result();
            }
        }
        return StoreResult.success();
    }

    /**
     * Записать чекпоинт, если счета накопились. Вызывается по расписанию платформы.
     *
     * <p>
     * Копились ли счета, знает сам провайдер: у него файл. Провайдеру без обслуживания сохранять нечего,
     * его {@code apply} уже зафиксировал операцию.
     */
    public void autosave() {
        if (readOnly || maintenance() == null) {
            return;
        }
        CheckpointResult written = checkpoint();
        if (!written.successful()) {
            log.warn(
                "Autosave could not write the checkpoint: {}",
                written.result()
                    .message()
                    .orElse("no reason given"));
        }
    }

    /** Отпустить носитель на остановке сервера. */
    public void close() {
        EconomyStore resolved = store.peek();
        if (resolved == null) {
            return;
        }
        try {
            resolved.close();
        } catch (RuntimeException failure) {
            log.warn("Provider {} failed to close: {}", resolved.id(), failure.toString());
        }
    }

    private StoreMaintenance maintenance() {
        EconomyStore active = store();
        return active instanceof StoreMaintenance ? (StoreMaintenance) active : null;
    }

    private EconomyStore store() {
        return store.get();
    }

    private StoreSnapshot snapshotOf(LedgerState current) {
        return StoreSnapshot.of(
            current.accounts(),
            current.lastSeq(),
            current.history()
                .records());
    }

    private StoreResult save(LedgerState current) {
        StoreResult stored = store().save(snapshotOf(current));
        if (stored.successful()) {
            state = current.withCheckpoint(current.lastSeq());
        }
        return stored;
    }

    private TransferResult pipeline(TransferRequest request, ChangeCause cause, Boolean floorBypass) {
        if (readOnly) {
            return refuse(request, ResultCode.READONLY, null);
        }
        long now = clock.getAsLong();
        CurrencyRecord currency = currencies.get(request.currencyId());
        if (currency == null) {
            return refuse(request, ResultCode.UNKNOWN_CURRENCY, null);
        }
        if (!limits.acceptsTransactionId(request.transactionId())) {
            return refuse(request, ResultCode.INVALID_REQUEST, null);
        }
        if (request.reason()
            .isPresent()
            && !limits.acceptsReason(
                request.reason()
                    .get())) {
            return refuse(request, ResultCode.INVALID_REQUEST, null);
        }
        ResultCode amountCode = amountCode(request, currency);
        if (amountCode != null) {
            return refuse(request, amountCode, null);
        }

        Optional<TransactionRecord> known = index.find(request.transactionId(), now, idempotencyMillis);
        if (known.isPresent()) {
            if (logChecks) {
                log.debug(
                    "Operation {} repeats the recorded outcome of seq {}",
                    request.transactionId(),
                    Long.valueOf(
                        known.get()
                            .seq()));
            }
            return TransferResult.duplicate(
                request.transactionId(),
                afterOf(
                    known.get()
                        .fromAfter()),
                afterOf(
                    known.get()
                        .toAfter()));
        }
        Optional<TransactionRecord> stale = index.known(request.transactionId());
        if (stale.isPresent()) {
            warn(
                "Operation {} repeats the id of seq {} outside the idempotency window and is applied again",
                request.transactionId(),
                Long.valueOf(
                    stale.get()
                        .seq()));
        }

        LedgerState current = state;
        AccountView from = null;
        AccountView to = null;
        if (request.from()
            .isPresent()) {
            from = resolve(
                current,
                request.from()
                    .get(),
                now);
            if (from == null) {
                return refuse(request, ResultCode.UNKNOWN_PLAYER, null);
            }
        }
        if (request.to()
            .isPresent()) {
            to = resolve(
                current,
                request.to()
                    .get(),
                now);
            if (to == null) {
                return refuse(request, ResultCode.UNKNOWN_PLAYER, null);
            }
        }
        if (request.kind() == TransactionRecord.Kind.TRANSFER && request.from()
            .equals(request.to())) {
            return refuse(request, ResultCode.SAME_ACCOUNT, null);
        }
        if ((from != null && from.frozen()) || (to != null && to.frozen())) {
            return refuse(request, ResultCode.ACCOUNT_FROZEN, from, to, null);
        }
        if (request.kind() == TransactionRecord.Kind.TRANSFER && !currency.payAllowed()) {
            return refuse(request, ResultCode.PAY_DISABLED, from, to, null);
        }

        long floor = bypass(request, floorBypass) ? currency.negativeFloor() : currency.minBalance();
        Map<UUID, AccountView> accounts = new LinkedHashMap<>(current.accounts());
        List<AccountView> upserts = new ArrayList<>(2);
        TransactionRecord.Builder record = TransactionRecord
            .builder(request.kind(), currency.id(), request.transactionId())
            .seq(current.lastSeq() + 1L)
            .ts(now)
            .cause(cause)
            .reason(
                request.reason()
                    .orElse(null));
        request.actor()
            .ifPresent(record::actor);
        Long fromAfter = null;
        Long toAfter = null;

        if (from != null) {
            if (!accounts.containsKey(from.uuid()) && accounts.size() >= limits.accounts()) {
                warn(
                    "Account ceiling of {} is reached, {} is refused",
                    Integer.valueOf(limits.accounts()),
                    from.uuid());
                return refuse(request, ResultCode.INVALID_REQUEST, from, to, null);
            }
            long before = balanceOf(current, from.uuid(), currency);
            long after = subtract(before, request.amount());
            if (after < floor) {
                return refuse(
                    request,
                    request.kind() == TransactionRecord.Kind.WITHDRAW ? ResultCode.BELOW_FLOOR
                        : ResultCode.INSUFFICIENT,
                    from,
                    to,
                    null);
            }
            fromAfter = Long.valueOf(after);
            AccountView updated = updated(from, from.uuid(), currency.id(), after, now);
            accounts.put(from.uuid(), updated);
            upserts.add(updated);
            record.from(from.uuid(), after);
        }
        if (to != null) {
            if (!accounts.containsKey(to.uuid()) && accounts.size() >= limits.accounts()) {
                warn("Account ceiling of {} is reached, {} is refused", Integer.valueOf(limits.accounts()), to.uuid());
                return refuse(request, ResultCode.INVALID_REQUEST, from, to, null);
            }
            long before = balanceOf(current, to.uuid(), currency);
            boolean exact = request.kind() == TransactionRecord.Kind.SET
                || request.kind() == TransactionRecord.Kind.RESET;
            long after = exact ? exactValue(request, currency, to.uuid()) : add(before, request.amount());
            if (after > currency.maxBalance()) {
                return refuse(request, ResultCode.ABOVE_CEILING, from, to, null);
            }
            if (after < floor) {
                return refuse(request, ResultCode.BELOW_FLOOR, from, to, null);
            }
            toAfter = Long.valueOf(after);
            AccountView updated = updated(to, to.uuid(), currency.id(), after, now);
            accounts.put(to.uuid(), updated);
            upserts.add(updated);
            record.to(to.uuid(), after);
        }

        Optional<GuardChain.Veto> veto = guards.get()
            .check(request, from, to);
        if (veto.isPresent()) {
            if (logChecks) {
                log.debug(
                    "Operation {} is vetoed by guard {}: {}",
                    request.transactionId(),
                    veto.get()
                        .guardId(),
                    veto.get()
                        .reason());
            }
            return refuse(
                request,
                ResultCode.GUARD_VETO,
                from,
                to,
                veto.get()
                    .reason());
        }

        ChangeBatch.Builder batchBuilder = ChangeBatch.builder(cause);
        for (AccountView upsert : upserts) {
            batchBuilder.upsert(upsert);
        }
        ChangeBatch batch = batchBuilder.append(record.build())
            .build();
        StoreResult stored = write(batch);
        if (!stored.successful()) {
            degrade(stored);
            return refuse(
                request,
                ResultCode.STORE_FAILURE,
                from,
                to,
                stored.message()
                    .orElse(null));
        }
        restore();

        TransactionRecord written = batch.records()
            .get(0);
        LedgerState next = current.next(accounts, written, historyEntries);
        state = next;
        index.remember(written);
        guards.get()
            .committed(request);
        sweep(now);

        events.transactions(Collections.singletonList(written));
        if (fromAfter != null) {
            events.change(from.uuid(), currency.id(), balanceOf(current, from.uuid(), currency), fromAfter.longValue());
        }
        if (toAfter != null) {
            events.change(to.uuid(), currency.id(), balanceOf(current, to.uuid(), currency), toAfter.longValue());
        }
        audit(written, request);
        return TransferResult.success(request.transactionId(), fromAfter, toAfter);
    }

    private ResultCode amountCode(TransferRequest request, CurrencyRecord currency) {
        if (request.kind() == TransactionRecord.Kind.RESET) {
            return request.amount() == 0L ? null : ResultCode.BAD_AMOUNT;
        }
        if (request.kind() != TransactionRecord.Kind.SET && request.amount() <= 0L) {
            return ResultCode.BAD_AMOUNT;
        }
        if (request.kind() != TransactionRecord.Kind.TRANSFER) {
            return null;
        }
        if (request.amount() < minTransfer) {
            return ResultCode.BAD_AMOUNT;
        }
        if (request.amount() > personalCeiling(
            request.from()
                .get(),
            currency)) {
            return ResultCode.ABOVE_CEILING;
        }
        return null;
    }

    private long exactValue(TransferRequest request, CurrencyRecord currency, UUID target) {
        if (request.kind() == TransactionRecord.Kind.SET) {
            return request.amount();
        }
        Optional<String> meta = meta(target, EconomyNodes.META_STARTING);
        if (!meta.isPresent()) {
            return currency.startBalance();
        }
        OptionalLong parsed = majorUnits(meta.get(), currency, EconomyNodes.META_STARTING);
        return parsed.isPresent() ? parsed.getAsLong() : currency.startBalance();
    }

    private long personalCeiling(UUID player, CurrencyRecord currency) {
        Optional<String> meta = meta(player, EconomyNodes.META_PAY_LIMIT);
        if (!meta.isPresent()) {
            return maxTransfer;
        }
        OptionalLong parsed = majorUnits(meta.get(), currency, EconomyNodes.META_PAY_LIMIT);
        return parsed.isPresent() ? parsed.getAsLong() : maxTransfer;
    }

    private OptionalLong majorUnits(String raw, CurrencyRecord currency, String key) {
        try {
            long value = Long.parseLong(raw.trim());
            if (value <= 0L) {
                throw new IllegalArgumentException("must be above zero");
            }
            return OptionalLong.of(Amounts.fromMajor(value, currency));
        } catch (RuntimeException failure) {
            warn("Meta {} carries unusable value {} and is treated as absent: {}", key, raw, failure.getMessage());
            return OptionalLong.empty();
        }
    }

    /** Мета группы с запасным отказом: ядро прав ещё не подняло, переводы не должны падать. */
    private Optional<String> meta(UUID player, String key) {
        try {
            return lookup.meta(player, key);
        } catch (RuntimeException failure) {
            warn("Meta {} for {} cannot be read and is treated as absent: {}", key, player, failure.toString());
            return Optional.empty();
        }
    }

    private boolean bypass(TransferRequest request, Boolean checked) {
        if (checked != null) {
            return checked.booleanValue();
        }
        Optional<UUID> actor = request.actor();
        if (!actor.isPresent()) {
            return false;
        }
        try {
            return lookup.has(actor.get(), EconomyNodes.BYPASS_MIN_BALANCE);
        } catch (RuntimeException failure) {
            warn(
                "Node {} for {} cannot be checked and is treated as absent: {}",
                EconomyNodes.BYPASS_MIN_BALANCE,
                actor.get(),
                failure.toString());
            return false;
        }
    }

    private AccountView resolve(LedgerState current, UUID player, long now) {
        AccountView known = current.accounts()
            .get(player);
        if (known != null) {
            return known;
        }
        String name;
        try {
            name = lookup.name(player);
        } catch (RuntimeException failure) {
            warn("Name of {} cannot be resolved and the account stays unknown: {}", player, failure.toString());
            return null;
        }
        if (name == null) {
            return null;
        }
        return AccountView.of(player, name, Collections.<String, Long>emptyMap(), false, now);
    }

    private AccountView updated(AccountView existing, UUID player, String currencyId, long value, long now) {
        Map<String, Long> balances = new LinkedHashMap<>(existing.balances());
        balances.put(currencyId, Long.valueOf(value));
        return AccountView.of(
            player,
            existing.name()
                .orElse(null),
            balances,
            existing.frozen(),
            existing.createdAt() > 0L ? existing.createdAt() : now);
    }

    private long balanceOf(LedgerState state, UUID player, CurrencyRecord currency) {
        AccountView account = state.accounts()
            .get(player);
        if (account == null) {
            return currency.startBalance();
        }
        Long stored = account.balances()
            .get(currency.id());
        return stored == null ? currency.startBalance() : stored.longValue();
    }

    private long subtract(long value, long amount) {
        try {
            return Math.subtractExact(value, amount);
        } catch (ArithmeticException overflow) {
            return Long.MIN_VALUE;
        }
    }

    private long add(long value, long amount) {
        try {
            return Math.addExact(value, amount);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private StoreResult write(ChangeBatch batch) {
        try {
            return store().apply(batch);
        } catch (RuntimeException failure) {
            log.error("Provider {} failed to store the batch: {}", store().id(), failure.toString(), failure);
            return StoreResult.failure(StoreResult.Failure.WRITE_FAILED, failure.toString());
        }
    }

    private void degrade(StoreResult stored) {
        if (degraded) {
            return;
        }
        degraded = true;
        String reason = stored.message()
            .orElse("storage refused the write");
        log.warn("Economy enters degraded mode, provider {}: {}", store().id(), reason);
        events.degraded(DegradedEvent.of(true, store().id(), reason));
    }

    private void restore() {
        if (!degraded) {
            return;
        }
        degraded = false;
        log.info("Economy leaves degraded mode, provider {} accepted a write again", store().id());
        events.degraded(DegradedEvent.of(false, store().id(), null));
    }

    private void sweep(long now) {
        if (historyMillis > 0L) {
            LedgerState current = state;
            RingHistory trimmed = current.history()
                .evictOlderThan(now - historyMillis);
            if (trimmed != current.history()) {
                state = current.withHistory(trimmed);
            }
        }
        if (idempotencyMillis > 0L) {
            index.forgetBefore(now - idempotencyMillis);
        }
    }

    private TransferResult refuse(TransferRequest request, ResultCode code, AccountView from, AccountView to,
        String detail) {
        if (logChecks) {
            log.debug(
                "Operation {} is refused with {}{}",
                request.transactionId(),
                code,
                detail == null ? "" : ": " + detail);
        }
        Long fromAfter = from == null ? null
            : Long.valueOf(balanceOf(state, from.uuid(), currencies.get(request.currencyId())));
        Long toAfter = to == null ? null
            : Long.valueOf(balanceOf(state, to.uuid(), currencies.get(request.currencyId())));
        if (fromAfter == null && toAfter == null) {
            return TransferResult.failure(code, request.transactionId());
        }
        return TransferResult.failure(code, request.transactionId(), fromAfter, toAfter);
    }

    private TransferResult refuse(TransferRequest request, ResultCode code, String detail) {
        return refuse(request, code, null, null, detail);
    }

    private void audit(TransactionRecord record, TransferRequest request) {
        if (!logChanges) {
            return;
        }
        StringBuilder line = new StringBuilder("Economy ").append(record.kind())
            .append(' ')
            .append(request.amount())
            .append(' ')
            .append(record.currencyId())
            .append(", cause ")
            .append(record.cause())
            .append(", actor ")
            .append(
                record.actor()
                    .map(UUID::toString)
                    .orElse("console"))
            .append(", tx ")
            .append(record.transactionId());
        record.from()
            .ifPresent(
                player -> line.append(", from ")
                    .append(player)
                    .append(" -> ")
                    .append(
                        record.fromAfter()
                            .getAsLong()));
        record.to()
            .ifPresent(
                player -> line.append(", to ")
                    .append(player)
                    .append(" -> ")
                    .append(
                        record.toAfter()
                            .getAsLong()));
        record.reason()
            .ifPresent(
                reason -> line.append(", reason ")
                    .append(reason));
        log.info("{}", line);
    }

    private void warn(String message, Object... arguments) {
        log.warn(message, arguments);
    }

    private static Long afterOf(OptionalLong value) {
        return value.isPresent() ? Long.valueOf(value.getAsLong()) : null;
    }
}
