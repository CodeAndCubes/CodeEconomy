package com.mrleonardos.codeeconomy.internal.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;
import com.mrleonardos.codeeconomy.internal.EconomyFixtures;

class RingHistoryTest {

    @Test
    void appendKeepsNewestFirst() {
        RingHistory history = RingHistory.empty()
            .append(deposit(1L, 1000L), 10)
            .append(deposit(2L, 2000L), 10);

        assertEquals(
            2L,
            history.records()
                .get(0)
                .seq());
        assertEquals(
            1L,
            history.records()
                .get(1)
                .seq());
        assertEquals(2, history.size());
        assertEquals(1000L, history.oldestTs());
    }

    @Test
    void ceilingKeepsOnlyTheNewestRecords() {
        RingHistory history = RingHistory.empty();
        for (long seq = 1L; seq <= 5L; seq++) {
            history = history.append(deposit(seq, seq * 1000L), 3);
        }

        assertEquals(3, history.size());
        assertEquals(Arrays.asList(5L, 4L, 3L), seqs(history.records()));
    }

    @Test
    void evictOlderThanDropsExpiredRecords() {
        RingHistory history = RingHistory.empty()
            .append(deposit(1L, 1000L), 10)
            .append(deposit(2L, 5000L), 10);

        RingHistory trimmed = history.evictOlderThan(5000L);

        assertEquals(1, trimmed.size());
        assertEquals(Arrays.asList(2L), seqs(trimmed.records()));
        assertSame(history, history.evictOlderThan(0L));
    }

    @Test
    void byPlayerPagesNewestFirstAndTouchesBothSides() {
        RingHistory history = RingHistory.empty()
            .append(transfer(1L, EconomyFixtures.ALICE, EconomyFixtures.BOB), 10)
            .append(transfer(2L, EconomyFixtures.BOB, EconomyFixtures.ALICE), 10)
            .append(transfer(3L, EconomyFixtures.ALICE, EconomyFixtures.CAROL), 10);

        assertEquals(Arrays.asList(3L, 2L), seqs(history.byPlayer(EconomyFixtures.ALICE, 0, 2)));
        assertTrue(
            history.byPlayer(EconomyFixtures.ALICE, 2, 2)
                .isEmpty());
        assertEquals(Arrays.asList(2L, 1L), seqs(history.byPlayer(EconomyFixtures.BOB, 0, 2)));
        assertTrue(
            history.byPlayer(EconomyFixtures.ALICE, -1, 2)
                .isEmpty());
    }

    @Test
    void ofKeepsTheNewestTailWithinTheCeiling() {
        List<TransactionRecord> oldestFirst = new ArrayList<>();
        for (long seq = 1L; seq <= 4L; seq++) {
            oldestFirst.add(deposit(seq, seq * 1000L));
        }

        RingHistory history = RingHistory.of(oldestFirst, 2);

        assertEquals(2, history.size());
        assertEquals(Arrays.asList(4L, 3L), seqs(history.records()));
    }

    private static List<Long> seqs(List<TransactionRecord> records) {
        List<Long> found = new ArrayList<>();
        for (TransactionRecord record : records) {
            found.add(Long.valueOf(record.seq()));
        }
        return found;
    }

    private static TransactionRecord deposit(long seq, long ts) {
        return TransactionRecord.builder(TransactionRecord.Kind.DEPOSIT, "coin", "tx" + seq)
            .seq(seq)
            .ts(ts)
            .to(EconomyFixtures.ALICE, 1000L)
            .cause(ChangeCause.COMMAND)
            .build();
    }

    private static TransactionRecord transfer(long seq, UUID from, UUID to) {
        return TransactionRecord.builder(TransactionRecord.Kind.TRANSFER, "coin", "tx" + seq)
            .seq(seq)
            .ts(seq * 1000L)
            .from(from, 900L)
            .to(to, 1100L)
            .cause(ChangeCause.COMMAND)
            .build();
    }
}
