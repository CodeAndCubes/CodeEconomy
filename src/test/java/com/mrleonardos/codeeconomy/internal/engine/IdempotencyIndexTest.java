package com.mrleonardos.codeeconomy.internal.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;
import com.mrleonardos.codeeconomy.internal.EconomyFixtures;

class IdempotencyIndexTest {

    private static final long HOUR = 3600L * 1000L;

    @Test
    void rememberAndFindReturnTheRecord() {
        IdempotencyIndex index = new IdempotencyIndex();
        TransactionRecord record = record(1L, 1000L, "shop:42");

        index.remember(record);

        assertEquals(Optional.of(record), index.find("shop:42", 2000L, 72 * HOUR));
        assertFalse(
            index.find("shop:43", 2000L, 72 * HOUR)
                .isPresent());
    }

    @Test
    void idOutsideTheWindowCountsAsNew() {
        IdempotencyIndex index = new IdempotencyIndex();
        index.remember(record(1L, 1000L, "shop:42"));

        assertFalse(
            index.find("shop:42", 1000L + 72 * HOUR, 72 * HOUR)
                .isPresent());
        assertTrue(
            index.find("shop:42", 1000L + 71 * HOUR, 72 * HOUR)
                .isPresent());
        assertTrue(
            index.find("shop:42", 1000L, 0L)
                .isPresent());
    }

    @Test
    void forgetBeforeEvictsOldEntries() {
        IdempotencyIndex index = new IdempotencyIndex();
        index.remember(record(1L, 1000L, "old"));
        index.remember(record(2L, 9000L, "new"));

        assertEquals(1, index.forgetBefore(5000L));

        assertEquals(1, index.size());
        assertFalse(
            index.find("old", 9000L, 0L)
                .isPresent());
        assertTrue(
            index.find("new", 9000L, 0L)
                .isPresent());
    }

    @Test
    void rebuildAfterRestartCatchesTheRepeat() {
        TransactionRecord first = record(1L, 1000L, "shop:42");
        TransactionRecord second = record(2L, 2000L, "shop:43");
        IdempotencyIndex index = new IdempotencyIndex();
        index.rebuild(Arrays.asList(first, second));

        assertEquals(Optional.of(first), index.find("shop:42", 3000L, 72 * HOUR));
        assertEquals(2, index.size());
    }

    private static TransactionRecord record(long seq, long ts, String transactionId) {
        return TransactionRecord.builder(TransactionRecord.Kind.DEPOSIT, "coin", transactionId)
            .seq(seq)
            .ts(ts)
            .to(EconomyFixtures.ALICE, 1000L)
            .cause(ChangeCause.COMMAND)
            .build();
    }
}
