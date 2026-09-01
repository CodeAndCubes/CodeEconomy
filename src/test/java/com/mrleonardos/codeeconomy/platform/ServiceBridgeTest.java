package com.mrleonardos.codeeconomy.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.CodeApi;
import com.mrleonardos.codecore.api.CoreRuntime;
import com.mrleonardos.codecore.api.command.CommandService;
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codecore.api.net.NetworkService;
import com.mrleonardos.codecore.api.service.ServicePriority;
import com.mrleonardos.codecore.api.service.ServiceRegistry;
import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codeeconomy.api.EconomyService;
import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.BalanceEntry;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;
import com.mrleonardos.codeeconomy.api.model.TransferRequest;
import com.mrleonardos.codeeconomy.api.model.TransferResult;

class ServiceBridgeTest {

    private static final RecordingRegistry REGISTRY = new RecordingRegistry();
    private static boolean installed;

    @BeforeAll
    static void installRuntime() {
        if (installed) {
            return;
        }
        try {
            CodeApi.install(new StubRuntime(REGISTRY));
            installed = true;
        } catch (IllegalStateException alreadyInstalled) {
            installed = CodeApi.services() == REGISTRY;
        }
    }

    @Test
    void bridgeHandsTheServiceToTheCoreAsBuiltin() {
        assertTrue(installed, "тесту нужен свой рантайм ядра");
        StubService service = new StubService();

        ServiceBridge.register(service);

        assertEquals(1, REGISTRY.registrations.size());
        Registration registration = REGISTRY.registrations.get(0);
        assertEquals(EconomyService.class, registration.type);
        assertSame(service, registration.implementation);
        assertEquals(ServicePriority.BUILTIN, registration.priority);
    }

    @Test
    void frozenRegistryRefusesTheRegistration() {
        assertTrue(installed, "тесту нужен свой рантайм ядра");
        REGISTRY.frozen = true;

        assertThrows(IllegalStateException.class, () -> ServiceBridge.register(new StubService()));
    }

    private static final class Registration {

        private final Class<?> type;
        private final Object implementation;
        private final ServicePriority priority;

        private Registration(Class<?> type, Object implementation, ServicePriority priority) {
            this.type = type;
            this.implementation = implementation;
            this.priority = priority;
        }
    }

    private static final class RecordingRegistry implements ServiceRegistry {

        private final List<Registration> registrations = new ArrayList<>();
        private boolean frozen;

        @Override
        public <T> void register(Class<T> type, T implementation, ServicePriority priority) {
            if (frozen) {
                throw new IllegalStateException("registry is frozen");
            }
            registrations.add(new Registration(type, implementation, priority));
        }

        @Override
        public <T> T require(Class<T> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Optional<T> find(Class<T> type) {
            return Optional.empty();
        }

        @Override
        public boolean frozen() {
            return frozen;
        }
    }

    private static final class StubRuntime implements CoreRuntime {

        private final ServiceRegistry registry;

        private StubRuntime(ServiceRegistry registry) {
            this.registry = registry;
        }

        @Override
        public ServiceRegistry services() {
            return registry;
        }

        @Override
        public ConfigService configs() {
            throw new UnsupportedOperationException();
        }

        @Override
        public NetworkService network() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Scheduler scheduler() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CommandService commands() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubService implements EconomyService {

        @Override
        public List<CurrencyRecord> currencies() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CurrencyRecord> currency(String currencyId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String defaultCurrencyId() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long balance(UUID player, String currencyId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean has(UUID player, long amount, String currencyId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<AccountView> account(UUID player) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransferResult transfer(TransferRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransferResult deposit(TransferRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransferResult withdraw(TransferRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransferResult set(TransferRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransferResult reset(TransferRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<TransferResult> submit(TransferRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TransactionRecord> history(UUID player, int page, int pageSize) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<BalanceEntry> top(String currencyId, int page, int pageSize) {
            throw new UnsupportedOperationException();
        }
    }
}
