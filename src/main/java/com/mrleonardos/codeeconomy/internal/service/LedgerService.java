package com.mrleonardos.codeeconomy.internal.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codeeconomy.api.EconomyApi;
import com.mrleonardos.codeeconomy.api.EconomyLimits;
import com.mrleonardos.codeeconomy.api.EconomyService;
import com.mrleonardos.codeeconomy.api.guard.TransferGuard;
import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.BalanceEntry;
import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;
import com.mrleonardos.codeeconomy.api.model.TransferRequest;
import com.mrleonardos.codeeconomy.api.model.TransferResult;
import com.mrleonardos.codeeconomy.api.store.EconomyStore;
import com.mrleonardos.codeeconomy.api.store.StoreResult;
import com.mrleonardos.codeeconomy.internal.EconomySettings;
import com.mrleonardos.codeeconomy.internal.engine.Ledger;
import com.mrleonardos.codeeconomy.internal.event.EventDispatcher;
import com.mrleonardos.codeeconomy.internal.guard.GuardChain;
import com.mrleonardos.codeeconomy.internal.guard.PayCooldownGuard;
import com.mrleonardos.codeeconomy.internal.store.JsonEconomyStore;
import com.mrleonardos.codeeconomy.internal.store.Recovery;

/**
 * Реализация {@link EconomyService}: деньги сервера.
 *
 * <p>
 * Чтения работают из любого потока по последнему записанному состоянию. Мутации исполняет один писатель
 * в главном потоке: вызов оттуда исполняется сразу, из чужого потока бросается
 * {@code IllegalStateException}, а {@code submit()} ставит операцию в ближайший тик. Внутренние
 * вызывающие, например команды, зовут {@link #execute(TransferRequest, ChangeCause)} с нужной причиной
 * для аудита.
 */
public final class LedgerService implements EconomyService {

    public static final String IMPLEMENTATION = "com.mrleonardos.codeeconomy.internal.service.LedgerService";

    private final Ledger ledger;
    private final JsonEconomyStore builtin;
    private final EconomyStore store;
    private final Scheduler scheduler;
    private final BooleanSupplier mainThread;
    private final LongSupplier ticks;

    private LedgerService(Ledger ledger, JsonEconomyStore builtin, EconomyStore store, Scheduler scheduler,
        BooleanSupplier mainThread, LongSupplier ticks) {
        this.ledger = ledger;
        this.builtin = builtin;
        this.store = store;
        this.scheduler = scheduler;
        this.mainThread = mainThread;
        this.ticks = ticks;
    }

    /**
     * Собрать сервис: провайдер по настройке, пустое состояние, слушатели и гварды на местах.
     *
     * <p>
     * Состояние мира на этот момент ещё не открыто, поэтому загрузка идет отдельно, в
     * {@link #loadWorld()} при старте мира.
     */
    public static LedgerService create(EconomySettings config, List<CurrencyRecord> currencies,
        ConfigFile<JsonObject> checkpointFile, Scheduler scheduler, BooleanSupplier mainThread, LongSupplier ticks,
        LongSupplier clock, PlayerLookup lookup, Logger log) {
        EconomyLimits limits = config.ceilings(log);
        long idempotencyMillis = config.idempotencyMillis();
        JsonEconomyStore builtin = new JsonEconomyStore(
            checkpointFile,
            limits,
            JsonEconomyStore.starting(currencies),
            readOnlyOnCorrupt(config, log),
            idempotencyMillis,
            clock,
            log);
        EconomyStore store = resolveProvider(config.provider(), builtin, log);
        GuardChain guards = GuardChain.of(builtinGuards(config, clock), config.guards.failOpen, log);
        EventDispatcher events = new EventDispatcher(log);
        Ledger ledger = new Ledger(
            store,
            builtin,
            currencies,
            config.currencyId(),
            config,
            limits,
            guards,
            events,
            lookup,
            clock,
            log);
        return new LedgerService(ledger, builtin, store, scheduler, mainThread, ticks);
    }

    /**
     * Цепочка для движка: гварды чужих модов плюс встроенный кулдаун переводов. Кулдаун встаёт
     * отдельным списком, а не через {@code EconomyApi.registerGuard}, чтобы не спорить с чужими модами
     * за заморозку реестра.
     */
    static List<TransferGuard> builtinGuards(EconomySettings config, LongSupplier clock) {
        List<TransferGuard> guards = new ArrayList<>(EconomyApi.guards());
        if (config.limits.payCooldownSeconds > 0) {
            guards.add(new PayCooldownGuard(config.limits.payCooldownSeconds, clock));
        }
        return guards;
    }

    static EconomyStore resolveProvider(String configured, JsonEconomyStore builtin, Logger log) {
        Optional<EconomyStore> foreign = EconomyApi.store(configured);
        if (foreign.isPresent()) {
            log.info("Economy storage provider is {}", configured);
            return foreign.get();
        }
        if (!JsonEconomyStore.ID.equals(configured)) {
            log.warn("Storage provider {} is not registered, falling back to {}", configured, JsonEconomyStore.ID);
        }
        return builtin;
    }

    private static boolean readOnlyOnCorrupt(EconomySettings config, Logger log) {
        if (EconomySettings.DEFAULT_ON_CORRUPT.equalsIgnoreCase(config.storage.onCorrupt)) {
            return true;
        }
        log.warn(
            "Unknown storage.onCorrupt value {}, the safe {} is used",
            config.storage.onCorrupt,
            EconomySettings.DEFAULT_ON_CORRUPT);
        return true;
    }

    @Override
    public List<CurrencyRecord> currencies() {
        return ledger.currencies();
    }

    @Override
    public Optional<CurrencyRecord> currency(String currencyId) {
        return ledger.currency(currencyId);
    }

    @Override
    public String defaultCurrencyId() {
        return ledger.defaultCurrencyId();
    }

    @Override
    public long balance(UUID player, String currencyId) {
        return ledger.balance(player, currencyId);
    }

    @Override
    public boolean has(UUID player, long amount, String currencyId) {
        return ledger.has(player, amount, currencyId);
    }

    @Override
    public Optional<AccountView> account(UUID player) {
        return ledger.state()
            .account(player);
    }

    @Override
    public TransferResult transfer(TransferRequest request) {
        return execute(request, ChangeCause.API);
    }

    @Override
    public TransferResult deposit(TransferRequest request) {
        return execute(request, ChangeCause.API);
    }

    @Override
    public TransferResult withdraw(TransferRequest request) {
        return execute(request, ChangeCause.API);
    }

    @Override
    public TransferResult set(TransferRequest request) {
        return execute(request, ChangeCause.API);
    }

    @Override
    public TransferResult reset(TransferRequest request) {
        return execute(request, ChangeCause.API);
    }

    @Override
    public CompletableFuture<TransferResult> submit(TransferRequest request) {
        CompletableFuture<TransferResult> done = new CompletableFuture<>();
        scheduler.onMainThread(() -> {
            try {
                done.complete(ledger.execute(request, ChangeCause.API));
            } catch (RuntimeException failure) {
                done.completeExceptionally(failure);
            }
        });
        return done;
    }

    @Override
    public List<TransactionRecord> history(UUID player, int page, int pageSize) {
        return ledger.history(player, page, pageSize);
    }

    @Override
    public List<BalanceEntry> top(String currencyId, int page, int pageSize) {
        return ledger.top(currencyId, page, pageSize, ticks.getAsLong());
    }

    /**
     * Провести операцию с причиной для аудита. Внутренний вход для команд и обслуживания, чужие моды
     * пользуются методами интерфейса.
     *
     * @throws IllegalStateException если вызов пришёл не из главного потока
     */
    public TransferResult execute(TransferRequest request, ChangeCause cause) {
        requireMainThread();
        return ledger.execute(request, cause);
    }

    /** Сверка журнала с текущим состоянием, отчёт называет расхождения. */
    public List<String> verify() {
        return ledger.verify();
    }

    /** Та же сверка с числом пропущенных строк: для отчёта администратору. */
    public Recovery.Verification verifyDetailed() {
        return ledger.verifyDetailed();
    }

    /** Принудительный снимок состояния. */
    public StoreResult checkpoint() {
        requireMainThread();
        return ledger.checkpoint();
    }

    /** Свежий чекпоинт и обрезка журнала. */
    public StoreResult compact() {
        requireMainThread();
        return ledger.compact();
    }

    /** Поднять состояние из провайдера и сообщить слушателям итог восстановления. Идёт при старте мира. */
    public void loadWorld() {
        ledger.start(store.load());
    }

    /** Записать чекпоинт, если счета накопились. */
    public void autosave() {
        ledger.autosave();
    }

    /** Граница тика: отдать слушателям накопленные изменения балансов. */
    public void tick() {
        ledger.events()
            .flushTick();
    }

    /** Закрыть носитель: журнал больше не принимает записи. */
    public void close() {
        if (builtin != null) {
            builtin.closeQuietly();
        }
    }

    /** Движок: для команд, которым нужны гварды, история и топ целиком. */
    public Ledger ledger() {
        return ledger;
    }

    public boolean readOnly() {
        return ledger.readOnly();
    }

    public boolean degraded() {
        return ledger.degraded();
    }

    private void requireMainThread() {
        if (!mainThread.getAsBoolean()) {
            throw new IllegalStateException(
                "Economy mutations run in the main thread only, use submit() from another one");
        }
    }
}
