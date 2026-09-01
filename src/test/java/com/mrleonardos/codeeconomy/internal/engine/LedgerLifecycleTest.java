package com.mrleonardos.codeeconomy.internal.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codeeconomy.api.CurrencyIds;
import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.ResultCode;
import com.mrleonardos.codeeconomy.api.store.CheckpointResult;
import com.mrleonardos.codeeconomy.internal.EconomyConfig;
import com.mrleonardos.codeeconomy.internal.EconomyConstants;
import com.mrleonardos.codeeconomy.internal.EconomyFixtures;
import com.mrleonardos.codeeconomy.internal.TestConfigs;
import com.mrleonardos.codeeconomy.internal.store.JsonEconomyStore;

/**
 * Жизненный цикл движка на настоящем json-хранилище: операции, перезапуск, чекпоинт, снова перезапуск.
 *
 * <p>
 * Проверки движка на подставном провайдере этот класс аварий не ловят: там связка чекпоинта и журнала
 * не участвует, а теряются деньги именно в ней.
 */
class LedgerLifecycleTest {

    private static final String COIN = CurrencyIds.DEFAULT;
    private static final long START = 25000L;

    @TempDir
    Path root;

    private long now = 1000L;

    @Test
    void moneyRaisedFromTheJournalSurvivesACheckpointAndTheNextStart() {
        Ledger first = ledger();
        assertEquals(ResultCode.OK, deposit(first, EconomyFixtures.ALICE, 5000L, "tx1"));
        assertTrue(
            first.checkpoint()
                .written());
        assertEquals(ResultCode.OK, deposit(first, EconomyFixtures.BOB, 7000L, "tx2"));
        first.close();

        Ledger afterCrash = ledger();
        assertEquals(START + 5000L, afterCrash.balance(EconomyFixtures.ALICE, COIN));
        assertEquals(START + 7000L, afterCrash.balance(EconomyFixtures.BOB, COIN), "журнал поднял Боба");

        assertEquals(ResultCode.OK, deposit(afterCrash, EconomyFixtures.ALICE, 1000L, "tx3"));
        CheckpointResult written = afterCrash.checkpoint();
        assertTrue(written.written());
        assertEquals(3L, written.seq());
        afterCrash.close();

        Ledger afterCheckpoint = ledger();
        assertEquals(START + 6000L, afterCheckpoint.balance(EconomyFixtures.ALICE, COIN));
        assertEquals(
            START + 7000L,
            afterCheckpoint.balance(EconomyFixtures.BOB, COIN),
            "деньги Боба пережили чекпоинт, сделанный после перезапуска");
    }

    @Test
    void compactAfterARestartKeepsEveryBalanceAndTheJournalStaysUsable() {
        Ledger first = ledger();
        deposit(first, EconomyFixtures.ALICE, 5000L, "tx1");
        first.checkpoint();
        deposit(first, EconomyFixtures.BOB, 7000L, "tx2");
        first.close();

        Ledger afterCrash = ledger();
        assertTrue(
            afterCrash.compact()
                .successful());
        assertEquals(
            2L,
            afterCrash.state()
                .checkpointSeq());
        afterCrash.close();

        Ledger afterCompact = ledger();
        assertEquals(START + 5000L, afterCompact.balance(EconomyFixtures.ALICE, COIN));
        assertEquals(START + 7000L, afterCompact.balance(EconomyFixtures.BOB, COIN));
        assertEquals(ResultCode.OK, deposit(afterCompact, EconomyFixtures.ALICE, 1L, "tx3"));
    }

    /**
     * Отметка ника идёт на каждом входе игрока. Пустая строка в журнале плюс fsync плюс полная
     * перезапись файла счетов на каждый вход это лаг тика за операцию, которая и так восстановится.
     */
    @Test
    void markNameWritesNoJournalLineAndNoCheckpoint() throws Exception {
        Ledger ledger = ledger();
        deposit(ledger, EconomyFixtures.ALICE, 5000L, "tx1");
        ledger.checkpoint();
        long journalBefore = Files.size(journalPath());
        String checkpointBefore = readCheckpoint();

        assertTrue(
            ledger.markName(EconomyFixtures.ALICE, "AliceRenamed")
                .successful());

        assertEquals(journalBefore, Files.size(journalPath()), "журнал не растёт от смены ника");
        assertEquals(checkpointBefore, readCheckpoint(), "файл счетов не переписывается на каждый вход");
    }

    /** Заморозка редка и обязана пережить падение процесса, поэтому чекпоинт для неё пишется сразу. */
    @Test
    void freezeIsWrittenToTheCheckpointRightAway() {
        Ledger first = ledger();
        deposit(first, EconomyFixtures.ALICE, 5000L, "tx1");
        assertTrue(
            first.setFrozen(EconomyFixtures.ALICE, true)
                .successful());
        first.close();

        Ledger afterCrash = ledger();

        assertTrue(
            afterCrash.state()
                .account(EconomyFixtures.ALICE)
                .get()
                .frozen());
        assertEquals(ResultCode.ACCOUNT_FROZEN, deposit(afterCrash, EconomyFixtures.ALICE, 1L, "tx2"));
    }

    @Test
    void quarantineKeepsTheLedgerReadOnlyUntilUnlock() throws Exception {
        Ledger first = ledger();
        deposit(first, EconomyFixtures.ALICE, 5000L, "tx1");
        deposit(first, EconomyFixtures.BOB, 7000L, "tx2");
        first.close();
        damageTheMiddleLine();

        Ledger quarantined = ledger();
        assertTrue(quarantined.readOnly());
        assertEquals(ResultCode.READONLY, deposit(quarantined, EconomyFixtures.ALICE, 1L, "tx3"));
        quarantined.close();

        Ledger secondStart = ledger();
        assertTrue(secondStart.readOnly(), "признак карантина пережил перезапуск");
        assertTrue(
            secondStart.liftReadOnly()
                .successful());
        assertFalse(secondStart.readOnly());
        assertEquals(ResultCode.OK, deposit(secondStart, EconomyFixtures.ALICE, 1L, "tx4"));
        secondStart.close();

        assertFalse(ledger().readOnly(), "снятый карантин не возвращается");
    }

    private void damageTheMiddleLine() throws Exception {
        List<String> lines = Files.readAllLines(journalPath(), StandardCharsets.UTF_8);
        Files.write(
            journalPath(),
            (lines.get(0) + "\nnot a json line at all\n" + lines.get(1) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private ResultCode deposit(Ledger ledger, java.util.UUID target, long amount, String transactionId) {
        return ledger.execute(EconomyFixtures.deposit(target, amount, transactionId), ChangeCause.COMMAND)
            .code();
    }

    private Ledger ledger() {
        EconomyConfig config = EconomyFixtures.config();
        return EconomyFixtures.ledger(
            store(),
            Collections.singletonList(EconomyFixtures.coin()),
            config,
            EconomyFixtures.lookup(),
            () -> now,
            EconomyFixtures.LOG);
    }

    private JsonEconomyStore store() {
        TestConfigs files = new TestConfigs(root);
        return new JsonEconomyStore(
            files.open(JsonEconomyStore.spec()),
            EconomyFixtures.settings()
                .ceilings(EconomyFixtures.LOG),
            JsonEconomyStore.starting(Collections.singletonList(EconomyFixtures.coin())),
            0L,
            () -> now,
            EconomyFixtures.LOG);
    }

    private Path journalPath() {
        return new TestConfigs(root).path(JsonEconomyStore.spec())
            .getParent()
            .resolve(EconomyConstants.JOURNAL_FILE);
    }

    private String readCheckpoint() throws Exception {
        return new String(
            Files.readAllBytes(new TestConfigs(root).path(JsonEconomyStore.spec())),
            StandardCharsets.UTF_8);
    }
}
