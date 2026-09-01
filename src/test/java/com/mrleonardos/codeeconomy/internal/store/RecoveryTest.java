package com.mrleonardos.codeeconomy.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;
import com.mrleonardos.codeeconomy.internal.EconomyFixtures;

class RecoveryTest {

    private static final long START = 25000L;

    @TempDir
    Path root;

    @Test
    void recoverAppliesRecordsAfterTheCheckpoint() throws Exception {
        Path journal = journal("recovery");
        write(journal, line(transfer(1L, 1000L, 25000L, 26000L)), line(deposit(2L, 2000L, 26500L)));

        Recovery.Result result = Recovery.recover(new LinkedHashMap<UUID, AccountView>(), journal, 0L, start(), null);

        assertEquals(
            2,
            result.accounts()
                .size());
        assertEquals(25000L, balance(result, EconomyFixtures.ALICE));
        assertEquals(26500L, balance(result, EconomyFixtures.BOB));
        assertEquals(
            2,
            result.records()
                .size());
        assertEquals(2L, result.replayed());
        assertTrue(
            result.findings()
                .isEmpty());
        assertFalse(result.tailTruncated());
        assertNull(result.corruption());
    }

    @Test
    void recordsAtOrBelowTheCheckpointDoNotMoveBalances() throws Exception {
        Path journal = journal("mixed");
        write(journal, line(transfer(1L, 1000L, 25000L, 26000L)), line(transfer(2L, 2000L, 24000L, 27000L)));

        Map<UUID, AccountView> checkpoint = new LinkedHashMap<>();
        checkpoint.put(
            EconomyFixtures.ALICE,
            AccountView
                .of(EconomyFixtures.ALICE, "Alice", Collections.singletonMap("coin", Long.valueOf(24000L)), false, 0L));
        checkpoint.put(
            EconomyFixtures.BOB,
            AccountView
                .of(EconomyFixtures.BOB, "Bob", Collections.singletonMap("coin", Long.valueOf(27000L)), false, 0L));

        Recovery.Result result = Recovery.recover(checkpoint, journal, 1L, start(), null);

        assertEquals(24000L, balance(result, EconomyFixtures.ALICE));
        assertEquals(27000L, balance(result, EconomyFixtures.BOB));
        assertEquals(
            2,
            result.records()
                .size());
        assertEquals(1L, result.replayed());
    }

    @Test
    void verifyNamesThePlantedAfterBalance() throws Exception {
        Path journal = journal("planted");
        write(journal, line(transfer(1L, 1000L, 25000L, 26000L)), line(deposit(2L, 2000L, 99000L)));

        Recovery.Result result = Recovery.recover(new LinkedHashMap<UUID, AccountView>(), journal, 0L, start(), null);
        assertTrue(
            result.findings()
                .isEmpty());

        List<TransactionRecord> newestFirst = Arrays.asList(
            result.records()
                .get(1),
            result.records()
                .get(0));
        Map<UUID, AccountView> actual = new LinkedHashMap<>();
        actual.put(
            EconomyFixtures.ALICE,
            AccountView
                .of(EconomyFixtures.ALICE, "Alice", Collections.singletonMap("coin", Long.valueOf(25000L)), false, 0L));
        actual.put(
            EconomyFixtures.BOB,
            AccountView
                .of(EconomyFixtures.BOB, "Bob", Collections.singletonMap("coin", Long.valueOf(26500L)), false, 0L));

        List<String> findings = Recovery.verify(actual, newestFirst, start(), true);

        assertEquals(1, findings.size());
        assertTrue(
            findings.get(0)
                .contains("seq 2"));
        assertTrue(
            findings.get(0)
                .contains("99000"));
    }

    @Test
    void verifyKeepsSilenceWhenJournalAndAccountsAgree() {
        TransactionRecord outbound = transfer(1L, 1000L, 25000L, 26000L);
        TransactionRecord inbound = deposit(2L, 2000L, 26500L);

        Map<UUID, AccountView> actual = new LinkedHashMap<>();
        actual.put(
            EconomyFixtures.ALICE,
            AccountView
                .of(EconomyFixtures.ALICE, "Alice", Collections.singletonMap("coin", Long.valueOf(25000L)), false, 0L));
        actual.put(
            EconomyFixtures.BOB,
            AccountView
                .of(EconomyFixtures.BOB, "Bob", Collections.singletonMap("coin", Long.valueOf(26500L)), false, 0L));

        assertTrue(
            Recovery.verify(actual, Arrays.asList(inbound, outbound), start(), true)
                .isEmpty());
    }

    @Test
    void incompleteHistoryKeepsSilenceAboutTheTotal() {
        TransactionRecord outbound = transfer(1L, 1000L, 25000L, 26000L);

        Map<UUID, AccountView> actual = new LinkedHashMap<>();
        actual.put(
            EconomyFixtures.ALICE,
            AccountView
                .of(EconomyFixtures.ALICE, "Alice", Collections.singletonMap("coin", Long.valueOf(25000L)), false, 0L));

        assertTrue(
            Recovery.verify(actual, Collections.singletonList(outbound), start(), false)
                .isEmpty());
    }

    @Test
    void setDownAndResetFromAboveStartAreNotFindings() {
        TransactionRecord setDown = TransactionRecord.builder(TransactionRecord.Kind.SET, "coin", "tx1")
            .seq(1L)
            .ts(1000L)
            .to(EconomyFixtures.ALICE, 1000L)
            .cause(ChangeCause.COMMAND)
            .build();
        TransactionRecord reset = TransactionRecord.builder(TransactionRecord.Kind.RESET, "coin", "tx2")
            .seq(2L)
            .ts(2000L)
            .to(EconomyFixtures.BOB, START)
            .cause(ChangeCause.COMMAND)
            .build();

        assertTrue(
            Recovery.verify(new LinkedHashMap<UUID, AccountView>(), Arrays.asList(reset, setDown), start(), false)
                .isEmpty());
    }

    private Path journal(String name) {
        return root.resolve(name + ".jsonl");
    }

    private static void write(Path path, String... lines) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(
                path,
                String.join("\n", Arrays.asList(lines))
                    .concat("\n")
                    .getBytes(StandardCharsets.UTF_8));
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String line(TransactionRecord record) {
        return JournalCodec.encode(record);
    }

    private static long balance(Recovery.Result result, UUID player) {
        return result.accounts()
            .get(player)
            .balances()
            .get("coin")
            .longValue();
    }

    private static Recovery.StartBalances start() {
        return new Recovery.StartBalances() {

            @Override
            public long starting(String currencyId) {
                return START;
            }
        };
    }

    private static TransactionRecord transfer(long seq, long ts, long fromAfter, long toAfter) {
        return TransactionRecord.builder(TransactionRecord.Kind.TRANSFER, "coin", "tx" + seq)
            .seq(seq)
            .ts(ts)
            .from(EconomyFixtures.ALICE, fromAfter)
            .to(EconomyFixtures.BOB, toAfter)
            .cause(ChangeCause.COMMAND)
            .build();
    }

    private static TransactionRecord deposit(long seq, long ts, long toAfter) {
        return TransactionRecord.builder(TransactionRecord.Kind.DEPOSIT, "coin", "tx" + seq)
            .seq(seq)
            .ts(ts)
            .to(EconomyFixtures.BOB, toAfter)
            .cause(ChangeCause.COMMAND)
            .build();
    }
}
