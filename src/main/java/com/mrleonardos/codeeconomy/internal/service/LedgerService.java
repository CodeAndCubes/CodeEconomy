package com.mrleonardos.codeeconomy.internal.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import org.apache.logging.log4j.Logger;

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
import com.mrleonardos.codeeconomy.api.store.CheckpointResult;
import com.mrleonardos.codeeconomy.api.store.EconomyStore;
import com.mrleonardos.codeeconomy.api.store.StoreResult;
import com.mrleonardos.codeeconomy.api.store.StoreVerification;
import com.mrleonardos.codeeconomy.internal.EconomyConfig;
import com.mrleonardos.codeeconomy.internal.engine.Ledger;
import com.mrleonardos.codeeconomy.internal.event.EventDispatcher;
import com.mrleonardos.codeeconomy.internal.guard.GuardChain;
import com.mrleonardos.codeeconomy.internal.guard.PayCooldownGuard;

/**
 * Реализация {@link EconomyService}: деньги сервера.
 *
 * <p>
 * Чтения работают из любого потока по последнему записанному состоянию. Мутации исполняет один писатель
 * в главном потоке: вызов оттуда исполняется сразу, из чужого потока бросается
 * {@code IllegalStateException}, а {@code submit()} ставит операцию в ближайший тик. Внутренние
 * вызывающие, например команды, зовут {@link #execute(TransferRequest, ChangeCause, boolean)} с нужной
 * причиной для аудита.
 */
public final class LedgerService implements EconomyService {

    public static final String IMPLEMENTATION = "com.mrleonardos.codeeconomy.internal.service.LedgerService";

    private final Ledger ledger;
    private final Scheduler scheduler;
    private final BooleanSupplier mainThread;
    private final LongSupplier ticks;

    private LedgerService(Ledger ledger, Scheduler scheduler, BooleanSupplier mainThread, LongSupplier ticks) {
        this.ledger = ledger;
        this.scheduler = scheduler;
        this.mainThread = mainThread;
        this.ticks = ticks;
    }

    /**
     * Собрать сервис на уже решённом провайдере: пустое состояние, слушатели на местах.
     *
     * <p>
     * Имя из {@code [storage] provider} разбирает {@code EconomyBootstrap} до этой точки: незнакомое
     * имя выключает экономику целиком, отката на встроенное нет. Цепочка гвардов выбирается лениво, при
     * первом обращении к деньгам: реестры {@code EconomyApi} открыты всю фазу инициализации, и мод,
     * загруженный после codeeconomy, регистрируется в своём init. Состояние мира на этот момент тоже не
     * открыто, поэтому загрузка идёт отдельно, в {@link #loadWorld()} при старте мира.
     */
    public static LedgerService create(EconomyStore store, EconomyConfig config, List<CurrencyRecord> currencies,
        Scheduler scheduler, BooleanSupplier mainThread, LongSupplier ticks, LongSupplier clock, PlayerLookup lookup,
        EventDispatcher events, Logger log) {
        EconomyLimits limits = config.ceilings(log);
        Ledger ledger = new Ledger(
            () -> store,
            currencies,
            config.currencyId(),
            config,
            limits,
            () -> GuardChain.of(builtinGuards(config, clock), config.failOpen(), log),
            events,
            lookup,
            clock,
            log);
        return new LedgerService(ledger, scheduler, mainThread, ticks);
    }

    /**
     * Цепочка для движка: гварды чужих модов плюс встроенный кулдаун переводов. Кулдаун встаёт
     * отдельным списком, а не через {@code EconomyApi.registerGuard}, чтобы не спорить с чужими модами
     * за заморозку реестра.
     */
    static List<TransferGuard> builtinGuards(EconomyConfig config, LongSupplier clock) {
        List<TransferGuard> guards = new ArrayList<>(EconomyApi.guards());
        if (config.payCooldownSeconds() > 0) {
            guards.add(new PayCooldownGuard(config.payCooldownSeconds(), clock));
        }
        return guards;
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
     * Провести операцию с причиной для аудита и решённым обходом пола. Внутренний вход для команд и
     * обслуживания, чужие моды пользуются методами интерфейса.
     *
     * @param floorBypass право {@code codeeconomy.bypass.minbalance} у отправителя команды, спрошенное
     *                    швом команд: для консоли, у которой нет uuid, движку это не узнать
     * @throws IllegalStateException если вызов пришёл не из главного потока
     */
    public TransferResult execute(TransferRequest request, ChangeCause cause, boolean floorBypass) {
        requireMainThread();
        return ledger.execute(request, cause, floorBypass);
    }

    private TransferResult execute(TransferRequest request) {
        requireMainThread();
        return ledger.execute(request, ChangeCause.API);
    }

    /** Сверка состояния с носителем: для отчёта администратору. */
    public StoreVerification verifyDetailed() {
        return ledger.verifyDetailed();
    }

    /** Принудительный снимок состояния. */
    public CheckpointResult checkpoint() {
        requireMainThread();
        return ledger.checkpoint();
    }

    /** Свежий чекпоинт и обрезка журнала. */
    public StoreResult compact() {
        requireMainThread();
        return ledger.compact();
    }

    /** Снять карантин носителя и открыть мутации. */
    public StoreResult unlock() {
        requireMainThread();
        return ledger.liftReadOnly();
    }

    /** Поднять состояние из провайдера и сообщить слушателям итог восстановления. Идёт при старте мира. */
    public void loadWorld() {
        ledger.loadWorld();
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

    /** Закрыть носитель: новых записей не будет. */
    public void close() {
        ledger.close();
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
