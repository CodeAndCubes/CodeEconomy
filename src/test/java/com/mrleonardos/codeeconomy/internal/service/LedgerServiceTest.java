package com.mrleonardos.codeeconomy.internal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codeeconomy.api.CurrencyIds;
import com.mrleonardos.codeeconomy.api.guard.TransferGuard;
import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.api.model.ResultCode;
import com.mrleonardos.codeeconomy.api.model.TransferResult;
import com.mrleonardos.codeeconomy.internal.EconomyConfig;
import com.mrleonardos.codeeconomy.internal.EconomyFixtures;
import com.mrleonardos.codeeconomy.internal.TestConfigs;
import com.mrleonardos.codeeconomy.internal.engine.Ledger;
import com.mrleonardos.codeeconomy.internal.event.EventDispatcher;
import com.mrleonardos.codeeconomy.internal.guard.PayCooldownGuard;

class LedgerServiceTest {

    private static final String COIN = CurrencyIds.DEFAULT;

    @TempDir
    Path root;

    private final AtomicLong now = new AtomicLong(1000L);
    private final AtomicLong tick = new AtomicLong(0L);
    private final List<Runnable> scheduled = new ArrayList<>();

    @Test
    void mutationsFromAnotherThreadAreRefused() {
        LedgerService service = service(EconomyFixtures.configs(), () -> false);

        assertThrows(
            IllegalStateException.class,
            () -> service
                .execute(EconomyFixtures.deposit(EconomyFixtures.ALICE, 10L, "tx1"), ChangeCause.COMMAND, false));
        assertThrows(
            IllegalStateException.class,
            () -> service.transfer(EconomyFixtures.deposit(EconomyFixtures.ALICE, 10L, "tx2")));
    }

    @Test
    void submitRunsInTheNearestTickAndCompletes() {
        LedgerService service = service(EconomyFixtures.configs(), () -> false);

        CompletableFuture<TransferResult> future = service
            .submit(EconomyFixtures.deposit(EconomyFixtures.ALICE, 300L, "shop:42"));
        assertFalse(future.isDone(), "пока тика не было, ответа нет");

        scheduled.forEach(Runnable::run);

        assertTrue(future.isDone());
        assertEquals(
            ResultCode.OK,
            future.join()
                .code());
        assertEquals(25300L, service.balance(EconomyFixtures.ALICE, COIN));
    }

    @Test
    void cooldownGuardIsBuiltOnlyForAPositiveSetting() {
        EconomyFixtures.Configs withCooldown = EconomyFixtures.configs();
        withCooldown.section.payCooldownSeconds = 5;

        assertTrue(containsCooldown(LedgerService.builtinGuards(withCooldown.build(), now::get)));

        EconomyFixtures.Configs silent = EconomyFixtures.configs();
        silent.section.payCooldownSeconds = 0;

        assertFalse(containsCooldown(LedgerService.builtinGuards(silent.build(), now::get)));
    }

    @Test
    void cooldownBlocksTheSecondTransferInARow() {
        EconomyFixtures.Configs config = EconomyFixtures.configs();
        config.section.payCooldownSeconds = 60;
        LedgerService service = service(config, () -> true);

        assertEquals(
            ResultCode.OK,
            service
                .execute(
                    EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 10L, "tx1"),
                    ChangeCause.COMMAND,
                    false)
                .code());
        assertEquals(
            ResultCode.GUARD_VETO,
            service
                .execute(
                    EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 10L, "tx2"),
                    ChangeCause.COMMAND,
                    false)
                .code());

        now.addAndGet(61L * 1000L);
        assertEquals(
            ResultCode.OK,
            service
                .execute(
                    EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 10L, "tx3"),
                    ChangeCause.COMMAND,
                    false)
                .code());
    }

    private LedgerService service(EconomyFixtures.Configs config, java.util.function.BooleanSupplier mainThread) {
        TestConfigs files = TestConfigs.of(root);
        List<CurrencyRecord> currencies = Collections.singletonList(EconomyFixtures.coin());
        EconomyConfig built = config.build();
        return LedgerService.create(
            EconomyFixtures.jsonStore(files, currencies, built.idempotencyMillis(), now::get),
            built,
            currencies,
            scheduler(),
            mainThread,
            tick::get,
            now::get,
            EconomyFixtures.lookup(),
            new EventDispatcher(EconomyFixtures.LOG),
            EconomyFixtures.LOG);
    }

    private Scheduler scheduler() {
        return new Scheduler() {

            @Override
            public void onMainThread(Runnable task) {
                scheduled.add(task);
            }

            @Override
            public void afterTicks(int ticks, Runnable task) {
                scheduled.add(task);
            }
        };
    }

    private static boolean containsCooldown(List<TransferGuard> guards) {
        for (TransferGuard guard : guards) {
            if (PayCooldownGuard.ID.equals(guard.id())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Кулдаун отсчитывается от записанного перевода. Раньше метка ставилась в проверке, до записи, и
     * отказ носителя запирал игрока на весь кулдаун за перевод, которого не было.
     */
    @Test
    void aRefusedWriteDoesNotStartTheCooldown() {
        EconomyFixtures.Configs config = EconomyFixtures.configs();
        config.section.payCooldownSeconds = 60;
        EconomyFixtures.MemoryStore store = new EconomyFixtures.MemoryStore();
        store.refuse = true;
        Ledger ledger = EconomyFixtures.ledger(
            store,
            Collections.singletonList(EconomyFixtures.coin()),
            config.build(),
            Collections.singletonList(new PayCooldownGuard(60, now::get)),
            EconomyFixtures.lookup(),
            now::get,
            EconomyFixtures.LOG);

        assertEquals(
            ResultCode.STORE_FAILURE,
            ledger
                .execute(
                    EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 10L, "tx1"),
                    ChangeCause.COMMAND)
                .code());

        store.refuse = false;
        assertEquals(
            ResultCode.OK,
            ledger
                .execute(
                    EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 10L, "tx2"),
                    ChangeCause.COMMAND)
                .code(),
            "перевод, которого не было, кулдаун не запускает");
    }
}
