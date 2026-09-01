package com.mrleonardos.codeeconomy.internal.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeeconomy.api.CurrencyIds;
import com.mrleonardos.codeeconomy.api.guard.GuardResult;
import com.mrleonardos.codeeconomy.api.guard.TransferGuard;
import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.ResultCode;
import com.mrleonardos.codeeconomy.api.model.TransferRequest;
import com.mrleonardos.codeeconomy.api.store.EconomyStore;
import com.mrleonardos.codeeconomy.internal.EconomyFixtures;
import com.mrleonardos.codeeconomy.internal.EconomySettings;
import com.mrleonardos.codeeconomy.internal.event.EventDispatcher;
import com.mrleonardos.codeeconomy.internal.guard.GuardChain;

/**
 * Момент, когда движок берёт провайдера и гварды.
 *
 * <p>
 * Реестры {@code EconomyApi} открыты всю фазу инициализации, а мод, загруженный после codeeconomy,
 * регистрируется в своём init. Посчитай движок провайдера в конструкторе, чужой SqlStore молча остался
 * бы за бортом: исключения нет, в логе строка про откат на json.
 */
class LedgerBindingTest {

    private static final String COIN = CurrencyIds.DEFAULT;

    @Test
    void theProviderIsTakenAtTheFirstUseNotAtConstruction() {
        EconomyFixtures.MemoryStore early = new EconomyFixtures.MemoryStore();
        EconomyFixtures.MemoryStore late = new EconomyFixtures.MemoryStore();
        AtomicReference<EconomyStore> registered = new AtomicReference<>(early);
        Ledger ledger = ledger(
            registered::get,
            () -> GuardChain.of(Collections.emptyList(), false, EconomyFixtures.LOG));

        registered.set(late);
        ledger.loadWorld();
        ledger.execute(EconomyFixtures.deposit(EconomyFixtures.ALICE, 100L, "tx1"), ChangeCause.API);

        assertTrue(early.applied.isEmpty(), "провайдер, выбранный до чужой регистрации, не участвует");
        assertEquals(1, late.applied.size(), "запись ушла тому, кто зарегистрировался позже");
    }

    @Test
    void theGuardChainIsBuiltAtTheFirstUseNotAtConstruction() {
        EconomyFixtures.MemoryStore store = new EconomyFixtures.MemoryStore();
        AtomicReference<GuardChain> chain = new AtomicReference<>(
            GuardChain.of(Collections.emptyList(), false, EconomyFixtures.LOG));
        Ledger ledger = ledger(() -> store, chain::get);

        chain.set(GuardChain.of(Collections.singletonList(denying()), false, EconomyFixtures.LOG));
        ledger.loadWorld();

        assertEquals(
            ResultCode.GUARD_VETO,
            ledger.execute(EconomyFixtures.deposit(EconomyFixtures.ALICE, 100L, "tx1"), ChangeCause.API)
                .code());
    }

    private static Ledger ledger(java.util.function.Supplier<EconomyStore> store,
        java.util.function.Supplier<GuardChain> guards) {
        EconomySettings config = EconomyFixtures.settings();
        return new Ledger(
            store,
            Collections.singletonList(EconomyFixtures.coin()),
            COIN,
            config,
            config.ceilings(EconomyFixtures.LOG),
            guards,
            new EventDispatcher(EconomyFixtures.LOG),
            EconomyFixtures.lookup(),
            () -> 1000L,
            EconomyFixtures.LOG);
    }

    private static TransferGuard denying() {
        return new TransferGuard() {

            @Override
            public String id() {
                return "late-guard";
            }

            @Override
            public int priority() {
                return 0;
            }

            @Override
            public GuardResult check(TransferRequest request, AccountView from, AccountView to) {
                return GuardResult.deny("registered after codeeconomy started up");
            }
        };
    }
}
