package com.mrleonardos.codeeconomy.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;
import com.mrleonardos.codeeconomy.api.store.ChangeBatch;
import com.mrleonardos.codeeconomy.api.store.EconomyStore;
import com.mrleonardos.codeeconomy.api.store.StoreResult;
import com.mrleonardos.codeeconomy.api.store.StoreSnapshot;
import com.mrleonardos.codeeconomy.internal.EconomyFixtures;
import com.mrleonardos.codeeconomy.internal.TestConfigs;

class JsonEconomyStoreTest {

    private static final long START = 25000L;

    @TempDir
    Path root;

    private TestConfigs configs;

    private TestConfigs configs() {
        if (configs == null) {
            configs = new TestConfigs(root);
        }
        return configs;
    }

    @Test
    void applyWritesTheJournalLineAndStagesTheCheckpoint() throws Exception {
        EconomyStore store = store();

        StoreResult result = store.apply(batch(record(1L, transfer(1000L)), account(ALICE_KEY, 26000L)));

        assertTrue(result.successful());
        String journal = new String(Files.readAllBytes(journalPath()), StandardCharsets.UTF_8);
        assertTrue(journal.contains("\"kind\":\"TRANSFER\""));
        assertTrue(journal.contains("\"transactionId\":\"tx1\""));

        TransactionRecord decoded = JournalCodec.decode(journal.trim());
        assertNotNull(decoded);
        assertEquals(1L, decoded.seq());
        assertEquals(
            26000L,
            decoded.toAfter()
                .getAsLong());
    }

    @Test
    void roundTripRestoresAccountsFromCheckpointAndJournal() {
        EconomyStore first = store();
        first.apply(batch(record(1L, transfer(1000L)), account(ALICE_KEY, 26000L)));
        first.apply(batch(record(2L, deposit(500L)), account(BOB_KEY, 25500L)));
        ((JsonEconomyStore) first).flushCheckpoint();

        StoreSnapshot snapshot = store().load();

        assertEquals(
            2,
            snapshot.accounts()
                .size());
        assertEquals(
            26000L,
            snapshot.accounts()
                .get(key(ALICE_KEY))
                .balances()
                .get("coin")
                .longValue());
        assertEquals(
            2,
            snapshot.transactions()
                .size());
        assertFalse(snapshot.readOnly());
        assertEquals(2L, snapshot.lastSeq());
    }

    @Test
    void incompleteLastLineIsDropped() throws Exception {
        EconomyStore first = store();
        first.apply(batch(record(1L, transfer(1000L)), account(ALICE_KEY, 26000L)));
        String journal = new String(Files.readAllBytes(journalPath()), StandardCharsets.UTF_8);
        Files.write(
            journalPath(),
            (journal + JournalCodec.encode(record(2L, deposit(500L))) + "\n{\"seq\":3,\"ts\":")
                .getBytes(StandardCharsets.UTF_8));

        StoreSnapshot snapshot = store().load();

        assertFalse(snapshot.readOnly());
        assertEquals(
            2,
            snapshot.transactions()
                .size());
        assertEquals(2L, snapshot.lastSeq());
    }

    @Test
    void damagedMiddleLineQuarantinesTheJournalAndGoesReadOnly() throws Exception {
        EconomyStore first = store();
        first.apply(batch(record(1L, transfer(1000L)), account(ALICE_KEY, 26000L)));
        first.apply(batch(record(2L, deposit(500L)), account(BOB_KEY, 25500L)));
        String journal = new String(Files.readAllBytes(journalPath()), StandardCharsets.UTF_8);
        String[] lines = journal.split("\n");
        Files.write(
            journalPath(),
            (lines[0] + "\nnot a json line at all\n" + lines[1] + "\n").getBytes(StandardCharsets.UTF_8));

        StoreSnapshot snapshot = store().load();

        assertTrue(snapshot.readOnly());
        assertTrue(
            snapshot.reason()
                .orElse("")
                .contains("quarantined"));
        assertTrue(Files.exists(journalPath().resolveSibling("journal.jsonl.quarantine")));
        assertEquals(
            1,
            snapshot.transactions()
                .size());
        assertEquals(1L, snapshot.lastSeq());
        assertTrue(
            ((JsonEconomyStore) store()).lastFindings()
                .isEmpty());
    }

    @Test
    void brokenCheckpointIsRebuiltFromTheJournal() throws Exception {
        EconomyStore first = store();
        first.apply(batch(record(1L, transfer(1000L)), account(ALICE_KEY, 26000L)));
        first.apply(batch(record(2L, deposit(500L)), account(BOB_KEY, 25500L)));
        Files.write(checkpointPath(), "{broken".getBytes(StandardCharsets.UTF_8));

        StoreSnapshot snapshot = store().load();

        assertFalse(snapshot.readOnly());
        assertTrue(Files.exists(checkpointPath().resolveSibling("accounts.json.broken")));
        assertEquals(
            2,
            snapshot.accounts()
                .size());
        assertEquals(
            2,
            snapshot.transactions()
                .size());
    }

    @Test
    void compactWritesTheCheckpointAndTruncatesTheJournal() throws Exception {
        EconomyStore first = store();
        first.apply(batch(record(1L, transfer(1000L)), account(ALICE_KEY, 26000L)));
        first.apply(batch(record(2L, deposit(500L)), account(BOB_KEY, 25500L)));

        StoreSnapshot state = first.load();
        assertTrue(
            first.save(state)
                .successful());

        assertEquals("", new String(Files.readAllBytes(journalPath()), StandardCharsets.UTF_8));
        StoreSnapshot after = store().load();
        assertEquals(
            2,
            after.accounts()
                .size());
        assertTrue(
            after.transactions()
                .isEmpty());
        assertEquals(2L, after.checkpointSeq());
    }

    @Test
    void checkpointFileIsWrittenOnlyWhenDirty() {
        JsonEconomyStore store = store();

        assertFalse(store.flushCheckpoint());
        store.apply(batch(record(1L, transfer(1000L)), account(ALICE_KEY, 26000L)));
        assertTrue(store.flushCheckpoint());
        assertFalse(store.flushCheckpoint());
    }

    @Test
    void unreadableJournalGoesReadOnlyAndKeepsTheEvidence() throws Exception {
        EconomyStore first = store();
        first.apply(batch(record(1L, transfer(1000L)), account(ALICE_KEY, 26000L)));
        Files.delete(journalPath());
        Files.createDirectory(journalPath());

        StoreSnapshot snapshot = first.load();

        assertTrue(snapshot.readOnly());
        assertTrue(
            snapshot.reason()
                .orElse("")
                .contains("cannot be read"));
        assertTrue(Files.exists(journalPath().resolveSibling("journal.jsonl.quarantine")));
    }

    @Test
    void compactKeepsTheIdempotencyTail() throws Exception {
        long now = 1000L * 1000L;
        JsonEconomyStore first = storeWithWindow(72L * 3600L * 1000L, now);
        first.apply(batch(record(1L, transfer(1000L)), account(ALICE_KEY, 26000L)));
        first.apply(batch(record(2L, deposit(500L)), account(BOB_KEY, 25500L)));

        StoreSnapshot state = first.load();
        assertTrue(
            first.save(state)
                .successful());

        String[] lines = new String(Files.readAllBytes(journalPath()), StandardCharsets.UTF_8).split("\n");
        assertEquals(2, lines.length);
        assertEquals(
            2L,
            storeWithWindow(72L * 3600L * 1000L, now).load()
                .lastSeq());
    }

    @Test
    void verifyComparesTheJournalTailWithCurrentAccounts() throws Exception {
        JsonEconomyStore first = store();
        first.apply(batch(record(1L, transfer(1000L)), account(ALICE_KEY, 26000L)));
        first.apply(batch(record(2L, deposit(500L)), account(BOB_KEY, 25500L)));
        first.flushCheckpoint();

        StoreSnapshot state = first.load();
        Recovery.Verification agreeing = first.verify(state.accounts());
        assertTrue(
            agreeing.findings()
                .isEmpty());

        java.util.Map<UUID, AccountView> tampered = new java.util.LinkedHashMap<>(state.accounts());
        tampered.put(
            key(ALICE_KEY),
            AccountView
                .of(key(ALICE_KEY), "Alice", java.util.Collections.singletonMap("coin", Long.valueOf(9L)), false, 1L));
        Recovery.Verification broken = first.verify(tampered);
        assertEquals(
            1,
            broken.findings()
                .size());
        assertTrue(
            broken.findings()
                .get(0)
                .contains("account holds 9"));
        assertEquals(2L, broken.skipped());
    }

    private static final String ALICE_KEY = "00000000-0000-0000-0000-0000000000a1";
    private static final String BOB_KEY = "00000000-0000-0000-0000-0000000000b2";

    private static UUID key(String value) {
        return UUID.fromString(value);
    }

    private Path checkpointPath() {
        return configs().worldPath("codeeconomy", "accounts");
    }

    private Path journalPath() {
        return checkpointPath().getParent()
            .resolve("journal.jsonl");
    }

    private JsonEconomyStore store() {
        TestConfigs files = new TestConfigs(root);
        return new JsonEconomyStore(
            files.open(JsonEconomyStore.spec()),
            EconomyFixtures.settings()
                .ceilings(),
            JsonEconomyStore.starting(java.util.Collections.singletonList(EconomyFixtures.coin())),
            true,
            0L,
            () -> 0L,
            EconomyFixtures.LOG);
    }

    private JsonEconomyStore storeWithWindow(long idempotencyMillis, long now) {
        TestConfigs files = new TestConfigs(root);
        return new JsonEconomyStore(
            files.open(JsonEconomyStore.spec()),
            EconomyFixtures.settings()
                .ceilings(),
            JsonEconomyStore.starting(java.util.Collections.singletonList(EconomyFixtures.coin())),
            true,
            idempotencyMillis,
            () -> now,
            EconomyFixtures.LOG);
    }

    private static ChangeBatch batch(TransactionRecord record, AccountView... accounts) {
        ChangeBatch.Builder builder = ChangeBatch.builder(ChangeCause.COMMAND);
        for (AccountView account : accounts) {
            builder.upsert(account);
        }
        return builder.append(record)
            .build();
    }

    private static TransactionRecord record(long seq, TransactionRecord seed) {
        TransactionRecord.Builder builder = TransactionRecord.builder(seed.kind(), "coin", "tx" + seq)
            .seq(seq)
            .ts(seq * 1000L)
            .cause(ChangeCause.COMMAND);
        if (seed.from()
            .isPresent()) {
            builder.from(
                seed.from()
                    .get(),
                seed.fromAfter()
                    .getAsLong());
        }
        if (seed.to()
            .isPresent()) {
            builder.to(
                seed.to()
                    .get(),
                seed.toAfter()
                    .getAsLong());
        }
        return builder.build();
    }

    private static TransactionRecord transfer(long amount) {
        return TransactionRecord.builder(TransactionRecord.Kind.TRANSFER, "coin", "tx1")
            .seq(1L)
            .ts(1000L)
            .from(key(ALICE_KEY), START)
            .to(key(BOB_KEY), START + amount)
            .cause(ChangeCause.COMMAND)
            .build();
    }

    private static TransactionRecord deposit(long amount) {
        return TransactionRecord.builder(TransactionRecord.Kind.DEPOSIT, "coin", "tx2")
            .seq(2L)
            .ts(2000L)
            .to(key(BOB_KEY), START + amount)
            .cause(ChangeCause.COMMAND)
            .build();
    }

    private static AccountView account(String owner, long amount) {
        return AccountView
            .of(key(owner), "Alice", java.util.Collections.singletonMap("coin", Long.valueOf(amount)), false, 1000L);
    }
}
