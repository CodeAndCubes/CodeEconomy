package com.mrleonardos.codeeconomy.api.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;

class StoreTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Test
    void batchCarriesCauseUpsertsAndRecords() {
        ChangeBatch batch = ChangeBatch.builder(ChangeCause.COMMAND)
            .upsert(account(1250L))
            .append(record(1L))
            .build();
        assertEquals(ChangeCause.COMMAND, batch.cause());
        assertEquals(
            1,
            batch.upserts()
                .size());
        assertEquals(
            1,
            batch.records()
                .size());
        assertEquals(
            batch,
            ChangeBatch.builder(ChangeCause.COMMAND)
                .upsert(account(1250L))
                .append(record(1L))
                .build());
    }

    @Test
    void batchKeepsListsUntouchable() {
        ChangeBatch batch = ChangeBatch.builder(ChangeCause.API)
            .upsert(account(1L))
            .build();
        assertThrows(
            UnsupportedOperationException.class,
            () -> batch.upserts()
                .clear());
    }

    @Test
    void emptyBatchIsRefused() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ChangeBatch.builder(ChangeCause.API)
                .build());
        assertThrows(NullPointerException.class, () -> ChangeBatch.builder(null));
        assertThrows(
            NullPointerException.class,
            () -> ChangeBatch.builder(ChangeCause.API)
                .upsert(null)
                .build());
        assertThrows(
            NullPointerException.class,
            () -> ChangeBatch.builder(ChangeCause.API)
                .append(null)
                .build());
    }

    @Test
    void snapshotHoldsAccountsCheckpointAndRecords() {
        Map<UUID, AccountView> accounts = new LinkedHashMap<>();
        accounts.put(ALICE, account(1250L));
        StoreSnapshot snapshot = StoreSnapshot.of(accounts, 10L, java.util.Arrays.asList(record(11L), record(12L)));
        assertEquals(
            1,
            snapshot.accounts()
                .size());
        assertEquals(10L, snapshot.checkpointSeq());
        assertEquals(
            2,
            snapshot.transactions()
                .size());
        assertEquals(12L, snapshot.lastSeq());
        assertFalse(snapshot.readOnly());
        assertFalse(
            snapshot.reason()
                .isPresent());
        assertEquals(snapshot, StoreSnapshot.of(accounts, 10L, java.util.Arrays.asList(record(11L), record(12L))));
    }

    @Test
    void snapshotCopiesWhatItIsGiven() {
        Map<UUID, AccountView> accounts = new LinkedHashMap<>();
        accounts.put(ALICE, account(1250L));
        java.util.List<TransactionRecord> records = new java.util.ArrayList<>();
        records.add(record(1L));
        StoreSnapshot snapshot = StoreSnapshot.of(accounts, 0L, records);
        accounts.put(UUID.fromString("00000000-0000-0000-0000-00000000000b"), account(1L));
        records.clear();
        assertEquals(
            1,
            snapshot.accounts()
                .size());
        assertEquals(
            1,
            snapshot.transactions()
                .size());
        assertThrows(
            UnsupportedOperationException.class,
            () -> snapshot.accounts()
                .clear());
    }

    @Test
    void readOnlySnapshotKeepsStateAndNamesTheReason() {
        StoreSnapshot source = StoreSnapshot.of(new LinkedHashMap<>(), 3L, java.util.Collections.emptyList());
        StoreSnapshot quarantined = source.readOnly("journal is quarantined");
        assertTrue(quarantined.readOnly());
        assertEquals(3L, quarantined.checkpointSeq());
        assertEquals(
            "journal is quarantined",
            quarantined.reason()
                .get());
        assertEquals(3L, quarantined.lastSeq());
    }

    @Test
    void emptySnapshotIsReadyForAFirstLaunch() {
        StoreSnapshot empty = StoreSnapshot.empty();
        assertTrue(
            empty.accounts()
                .isEmpty());
        assertTrue(
            empty.transactions()
                .isEmpty());
        assertEquals(0L, empty.checkpointSeq());
        assertEquals(0L, empty.lastSeq());
        assertFalse(empty.readOnly());
    }

    @Test
    void resultSpeaksWithFiniteReasons() {
        assertTrue(
            StoreResult.success()
                .successful());
        assertFalse(
            StoreResult.success()
                .failure()
                .isPresent());
        assertEquals(2, StoreResult.Failure.values().length);

        StoreResult refusal = StoreResult.failure(StoreResult.Failure.WRITE_FAILED, "disk is full");
        assertFalse(refusal.successful());
        assertEquals(
            StoreResult.Failure.WRITE_FAILED,
            refusal.failure()
                .get());
        assertEquals(
            "disk is full",
            refusal.message()
                .get());
        assertEquals(refusal, StoreResult.failure(StoreResult.Failure.WRITE_FAILED, "disk is full"));
        assertThrows(NullPointerException.class, () -> StoreResult.failure(null, "причина"));
    }

    @Test
    void defaultSaveSpeaksOfUnsupported() {
        StubStore store = new StubStore();
        assertEquals("stub", store.id());
        assertEquals(StoreSnapshot.empty(), store.load());
        assertTrue(
            store.apply(
                ChangeBatch.builder(ChangeCause.API)
                    .upsert(account(1L))
                    .build())
                .successful());

        StoreResult save = store.save(StoreSnapshot.empty());
        assertFalse(save.successful());
        assertEquals(
            StoreResult.Failure.UNSUPPORTED,
            save.failure()
                .get());
        assertTrue(
            save.message()
                .get()
                .contains("stub"));
    }

    private static AccountView account(long amount) {
        Map<String, Long> balances = new LinkedHashMap<>();
        balances.put("coin", amount);
        return AccountView.of(ALICE, "Alice", balances, false, 0L);
    }

    private static TransactionRecord record(long seq) {
        return TransactionRecord.builder(TransactionRecord.Kind.DEPOSIT, "coin", "cmd:" + seq)
            .seq(seq)
            .ts(1000L + seq)
            .to(ALICE, 100L + seq)
            .cause(ChangeCause.COMMAND)
            .build();
    }

    static final class StubStore implements EconomyStore {

        @Override
        public String id() {
            return "stub";
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
}
