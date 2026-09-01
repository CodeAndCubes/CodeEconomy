package com.mrleonardos.codeeconomy.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeeconomy.api.event.EconomyEvents;
import com.mrleonardos.codeeconomy.api.event.EconomyListener;
import com.mrleonardos.codeeconomy.api.guard.GuardResult;
import com.mrleonardos.codeeconomy.api.guard.TransferGuard;
import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.BalanceEntry;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.api.model.ResultCode;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;
import com.mrleonardos.codeeconomy.api.model.TransferRequest;
import com.mrleonardos.codeeconomy.api.model.TransferResult;
import com.mrleonardos.codeeconomy.api.store.ChangeBatch;
import com.mrleonardos.codeeconomy.api.store.EconomyStore;
import com.mrleonardos.codeeconomy.api.store.StoreResult;
import com.mrleonardos.codeeconomy.api.store.StoreSnapshot;

class EconomyApiTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Test
    void unknownStoreIsAbsent() {
        assertFalse(
            EconomyApi.store("absent")
                .isPresent());
    }

    @Test
    void registriesAcceptRegistrationsUntilTheyFreeze() {
        StubStore builtin = new StubStore("json");
        StubStore sql = new StubStore("sql");
        StubStore replacement = new StubStore("sql");
        StubGuard paylimit = new StubGuard("paylimit", 10);
        StubGuard cooldown = new StubGuard("cooldown", 20);
        StubGuard cooldownAgain = new StubGuard("cooldown", 20);

        EconomyApi.registerStore(builtin);
        EconomyApi.registerStore(sql);
        EconomyApi.registerGuard(paylimit);
        EconomyApi.registerGuard(cooldown);
        assertSame(
            sql,
            EconomyApi.store("sql")
                .get());

        EconomyApi.registerStore(replacement);
        EconomyApi.registerGuard(cooldownAgain);
        assertSame(
            replacement,
            EconomyApi.store("sql")
                .get());
        assertTrue(
            EconomyApi.guards()
                .contains(paylimit));
        assertTrue(
            EconomyApi.guards()
                .contains(cooldownAgain));
        assertFalse(
            EconomyApi.guards()
                .contains(cooldown));

        EconomyApi.freeze();
        assertThrows(IllegalStateException.class, () -> EconomyApi.registerStore(new StubStore("mongo")));
        assertThrows(IllegalStateException.class, () -> EconomyApi.registerGuard(new StubGuard("escrow", 30)));
        assertSame(
            replacement,
            EconomyApi.store("sql")
                .get());
    }

    @Test
    void nullRegistrationIsRefused() {
        assertThrows(NullPointerException.class, () -> EconomyApi.registerStore(null));
        assertThrows(NullPointerException.class, () -> EconomyApi.registerGuard(null));
        assertThrows(NullPointerException.class, () -> EconomyApi.store(null));
    }

    @Test
    void serviceAndEventsSpeakUpWhenNotReady() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class, EconomyApi::service);
        assertTrue(
            refusal.getMessage()
                .contains("init"));
        assertThrows(IllegalStateException.class, EconomyApi::events);

        StubService service = new StubService();
        StubEvents events = new StubEvents();
        EconomyApi.install(() -> service, events);
        assertSame(service, EconomyApi.service());
        assertSame(events, EconomyApi.events());
        assertThrows(NullPointerException.class, () -> EconomyApi.install(null, events));
        assertThrows(NullPointerException.class, () -> EconomyApi.install(() -> service, null));
    }

    /**
     * Дверь через {@code EconomyApi} и реестр сервисов ядра обязаны вести к одной реализации. Пока
     * точка входа держала свой леджер, мод, вытеснивший экономику в реестре, отвечал одними балансами,
     * а {@code EconomyApi.service()} другими.
     */
    @Test
    void serviceFollowsWhoeverHoldsItInTheRegistry() {
        StubService builtin = new StubService();
        StubService replacement = new StubService();
        java.util.concurrent.atomic.AtomicReference<EconomyService> registry = new java.util.concurrent.atomic.AtomicReference<>(
            builtin);
        EconomyApi.install(registry::get, new StubEvents());

        assertSame(builtin, EconomyApi.service());

        registry.set(replacement);
        assertSame(replacement, EconomyApi.service(), "подмена в реестре видна и через точку входа");
    }

    /** Хранилище-заглушка для проверки реестра по имени. */
    static class StubStore implements EconomyStore {

        private final String id;

        StubStore(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public StoreSnapshot load() {
            return StoreSnapshot.empty();
        }

        @Override
        public StoreResult apply(ChangeBatch batch) {
            return StoreResult.success();
        }
    }

    /** Гвард-заглушка, всегда пускает. */
    static class StubGuard implements TransferGuard {

        private final String id;

        private final int priority;

        StubGuard(String id, int priority) {
            this.id = id;
            this.priority = priority;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public GuardResult check(TransferRequest request, AccountView from, AccountView to) {
            return GuardResult.allow();
        }
    }

    /** Сервис-заглушка: проверяется сама установка, а не поведение денег. */
    static class StubService implements EconomyService {

        @Override
        public List<CurrencyRecord> currencies() {
            return new ArrayList<>();
        }

        @Override
        public Optional<CurrencyRecord> currency(String currencyId) {
            return Optional.empty();
        }

        @Override
        public String defaultCurrencyId() {
            return "stub";
        }

        @Override
        public long balance(UUID player, String currencyId) {
            return 0L;
        }

        @Override
        public boolean has(UUID player, long amount, String currencyId) {
            return false;
        }

        @Override
        public Optional<AccountView> account(UUID player) {
            return Optional.empty();
        }

        @Override
        public TransferResult transfer(TransferRequest request) {
            return TransferResult.failure(ResultCode.INVALID_REQUEST, request.transactionId());
        }

        @Override
        public TransferResult deposit(TransferRequest request) {
            return TransferResult.failure(ResultCode.INVALID_REQUEST, request.transactionId());
        }

        @Override
        public TransferResult withdraw(TransferRequest request) {
            return TransferResult.failure(ResultCode.INVALID_REQUEST, request.transactionId());
        }

        @Override
        public TransferResult set(TransferRequest request) {
            return TransferResult.failure(ResultCode.INVALID_REQUEST, request.transactionId());
        }

        @Override
        public TransferResult reset(TransferRequest request) {
            return TransferResult.failure(ResultCode.INVALID_REQUEST, request.transactionId());
        }

        @Override
        public java.util.concurrent.CompletableFuture<TransferResult> submit(TransferRequest request) {
            return java.util.concurrent.CompletableFuture.completedFuture(transfer(request));
        }

        @Override
        public List<TransactionRecord> history(UUID player, int page, int pageSize) {
            return new ArrayList<>();
        }

        @Override
        public List<BalanceEntry> top(String currencyId, int page, int pageSize) {
            return new ArrayList<>();
        }
    }

    /** Реестр слушателей-заглушка. */
    static class StubEvents implements EconomyEvents {

        @Override
        public void register(int priority, EconomyListener listener) {}

        @Override
        public void unregister(EconomyListener listener) {}

        @Override
        public List<EconomyListener> listeners() {
            return new ArrayList<>();
        }
    }
}
