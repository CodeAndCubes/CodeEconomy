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
import com.mrleonardos.codeeconomy.api.model.ResultCode;
import com.mrleonardos.codeeconomy.api.model.TransferResult;
import com.mrleonardos.codeeconomy.internal.EconomyFixtures;
import com.mrleonardos.codeeconomy.internal.EconomySettings;
import com.mrleonardos.codeeconomy.internal.TestConfigs;
import com.mrleonardos.codeeconomy.internal.guard.PayCooldownGuard;
import com.mrleonardos.codeeconomy.internal.store.JsonEconomyStore;

class LedgerServiceTest {

    private static final String COIN = CurrencyIds.DEFAULT;

    @TempDir
    Path root;

    private final AtomicLong now = new AtomicLong(1000L);
    private final AtomicLong tick = new AtomicLong(0L);
    private final List<Runnable> scheduled = new ArrayList<>();

    @Test
    void mutationsFromAnotherThreadAreRefused() {
        LedgerService service = service(EconomyFixtures.settings(), () -> false);

        assertThrows(
            IllegalStateException.class,
            () -> service.execute(EconomyFixtures.deposit(EconomyFixtures.ALICE, 10L, "tx1"), ChangeCause.COMMAND));
        assertThrows(
            IllegalStateException.class,
            () -> service.transfer(EconomyFixtures.deposit(EconomyFixtures.ALICE, 10L, "tx2")));
    }

    @Test
    void submitRunsInTheNearestTickAndCompletes() {
        LedgerService service = service(EconomyFixtures.settings(), () -> false);

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
        EconomySettings withCooldown = EconomyFixtures.settings();
        withCooldown.limits.payCooldownSeconds = 5;

        assertTrue(containsCooldown(LedgerService.builtinGuards(withCooldown, now::get)));

        EconomySettings silent = EconomyFixtures.settings();
        silent.limits.payCooldownSeconds = 0;

        assertFalse(containsCooldown(LedgerService.builtinGuards(silent, now::get)));
    }

    @Test
    void cooldownBlocksTheSecondTransferInARow() {
        EconomySettings config = EconomyFixtures.settings();
        config.limits.payCooldownSeconds = 60;
        LedgerService service = service(config, () -> true);

        assertEquals(
            ResultCode.OK,
            service
                .execute(
                    EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 10L, "tx1"),
                    ChangeCause.COMMAND)
                .code());
        assertEquals(
            ResultCode.GUARD_VETO,
            service
                .execute(
                    EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 10L, "tx2"),
                    ChangeCause.COMMAND)
                .code());

        now.addAndGet(61L * 1000L);
        assertEquals(
            ResultCode.OK,
            service
                .execute(
                    EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 10L, "tx3"),
                    ChangeCause.COMMAND)
                .code());
    }

    private LedgerService service(EconomySettings config, java.util.function.BooleanSupplier mainThread) {
        // другой тест регистрирует в общем реестре EconomyApi чужого провайдера json, встроенного
        // получаем через несуществующее имя
        config.storage.provider = "builtin-under-test";
        TestConfigs files = new TestConfigs(root);
        return LedgerService.create(
            config,
            Collections.singletonList(EconomyFixtures.coin()),
            files.open(JsonEconomyStore.spec()),
            scheduler(),
            mainThread,
            tick::get,
            now::get,
            EconomyFixtures.lookup(),
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
}
