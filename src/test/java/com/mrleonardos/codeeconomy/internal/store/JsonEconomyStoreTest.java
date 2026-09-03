package com.mrleonardos.codeeconomy.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codeeconomy.EconomyConstants;
import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;
import com.mrleonardos.codeeconomy.api.store.ChangeBatch;
import com.mrleonardos.codeeconomy.api.store.CheckpointResult;
import com.mrleonardos.codeeconomy.api.store.StoreResult;
import com.mrleonardos.codeeconomy.api.store.StoreSnapshot;
import com.mrleonardos.codeeconomy.api.store.StoreVerification;
import com.mrleonardos.codeeconomy.internal.EconomyFixtures;
import com.mrleonardos.codeeconomy.internal.TestConfigs;

class JsonEconomyStoreTest {

    private static final long START = 25000L;
    private static final long WINDOW = 72L * 3600L * 1000L;

    @TempDir
    Path root;

    private long now = 1000L * 1000L;

    @Test
    void applyWritesTheJournalLineAndStagesTheCheckpoint() throws Exception {
        JsonEconomyStore store = store();

        StoreResult result = store.apply(batch(record(1L, transfer(1000L)), account(ALICE_KEY, 26000L)));

        assertTrue(result.successful());
        String journal = new String(Files.readAllBytes(journalPath()), StandardCharsets.UTF_8);
        assertTrue(journal.contains("\"kind\":\"TRANSFER\""));
        assertTrue(journal.contains("\"transactionId\":\"tx1\""));
        assertTrue(
            journal.contains("\"from\":\"" + ALICE_KEY + "\"") && journal.contains("\"to\":\"" + BOB_KEY + "\""),
            "участники записаны каноническими 36 знаками UUID: журнал переживает обновление мода, и "
                + "менять вид этой строки задним числом нельзя");

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
        JsonEconomyStore first = store();
        first.apply(batch(record(1L, transfer(1000L)), account(ALICE_KEY, 26000L)));
        first.apply(batch(record(2L, deposit(500L)), account(BOB_KEY, 25500L)));
        first.checkpoint(stateOf(2L, account(ALICE_KEY, 26000L), account(BOB_KEY, 25500L)));

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

    /**
     * Находка про потерю денег на первом же автосейве после аварийного старта: чекпоинт двигал границу
     * за все записи журнала, а в файл клал только изменившиеся счета. После восстановления накопитель
     * был пуст, и поднятые из журнала балансы уходили в никуда.
     */
    @Test
    void balancesRaisedFromTheJournalSurviveTheNextCheckpoint() {
        JsonEconomyStore first = store();
        first.apply(batch(record(1L, deposit(BOB_KEY, 30000L)), account(BOB_KEY, 30000L)));
        first.checkpoint(stateOf(1L, account(BOB_KEY, 30000L)));
        first.apply(batch(record(2L, deposit(CAROL_KEY, 31000L)), account(CAROL_KEY, 31000L)));
        first.apply(batch(record(3L, deposit(DAVE_KEY, 32000L)), account(DAVE_KEY, 32000L)));

        JsonEconomyStore afterCrash = store();
        StoreSnapshot recovered = afterCrash.load();
        assertEquals(31000L, balance(recovered, CAROL_KEY));
        assertEquals(32000L, balance(recovered, DAVE_KEY));

        afterCrash.apply(batch(record(4L, deposit(ALICE_KEY, 26000L)), account(ALICE_KEY, 26000L)));
        CheckpointResult written = afterCrash.checkpoint(
            stateOf(
                4L,
                account(BOB_KEY, 30000L),
                account(CAROL_KEY, 31000L),
                account(DAVE_KEY, 32000L),
                account(ALICE_KEY, 26000L)));
        assertTrue(written.written());
        assertEquals(4L, written.seq());

        StoreSnapshot afterRestart = store().load();
        assertEquals(31000L, balance(afterRestart, CAROL_KEY), "деньги Кэрол пережили чекпоинт");
        assertEquals(32000L, balance(afterRestart, DAVE_KEY), "деньги Дэйва пережили чекпоинт");
        assertEquals(30000L, balance(afterRestart, BOB_KEY));
        assertEquals(26000L, balance(afterRestart, ALICE_KEY));
    }

    /**
     * Находка про {@code /eco compact} после аварийного старта: полная выгрузка писала только
     * накопленное, а журнал после этого обрезался. Движения между чекпоинтом и падением исчезали.
     */
    @Test
    void compactAfterRecoveryKeepsEveryBalance() {
        JsonEconomyStore first = store();
        first.apply(batch(record(1L, deposit(BOB_KEY, 30000L)), account(BOB_KEY, 30000L)));
        first.checkpoint(stateOf(1L, account(BOB_KEY, 30000L)));
        first.apply(batch(record(2L, deposit(CAROL_KEY, 37000L)), account(CAROL_KEY, 37000L)));

        JsonEconomyStore afterCrash = store();
        StoreSnapshot recovered = afterCrash.load();
        assertTrue(
            afterCrash.save(StoreSnapshot.of(recovered.accounts(), recovered.lastSeq(), recovered.transactions()))
                .successful());

        StoreSnapshot afterRestart = store().load();
        assertEquals(37000L, balance(afterRestart, CAROL_KEY), "compact не съел движение после чекпоинта");
        assertEquals(30000L, balance(afterRestart, BOB_KEY));
        assertEquals(2L, afterRestart.checkpointSeq());
    }

    @Test
    void incompleteLastLineIsDropped() throws Exception {
        JsonEconomyStore first = store();
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
        damageTheMiddleLine();

        StoreSnapshot snapshot = store().load();

        assertTrue(snapshot.readOnly());
        assertTrue(
            snapshot.reason()
                .orElse("")
                .contains("quarantined"));
        assertTrue(Files.exists(journalPath().resolveSibling(EconomyConstants.JOURNAL_FILE + ".quarantine")));
        assertEquals(
            1,
            snapshot.transactions()
                .size());
        assertEquals(1L, snapshot.lastSeq());
        assertTrue(
            snapshot.findings()
                .isEmpty());
    }

    /**
     * Находка про молчаливый второй старт: журнал после карантина удалён, и без признака в состоянии
     * мира следующий запуск считал носитель здоровым, а всё после чекпоинта терял без единой строки в
     * логе.
     */
    @Test
    void quarantineMarkKeepsTheModReadOnlyAcrossRestarts() throws Exception {
        damageTheMiddleLine();
        assertTrue(
            store().load()
                .readOnly());

        StoreSnapshot second = store().load();

        assertTrue(second.readOnly(), "второй старт тоже поднимается только для чтения");
        assertTrue(
            second.reason()
                .orElse("")
                .contains("quarantined"));
        assertEquals(26000L, balance(second, BOB_KEY), "то, что удалось поднять, ушло в чекпоинт");
        assertEquals(1L, second.checkpointSeq());
    }

    @Test
    void unlockClearsTheMarkAndTheNextStartIsWritable() throws Exception {
        damageTheMiddleLine();
        JsonEconomyStore quarantined = store();
        assertTrue(
            quarantined.load()
                .readOnly());

        assertTrue(
            quarantined.liftReadOnly()
                .successful());

        assertFalse(
            store().load()
                .readOnly());
    }

    @Test
    void brokenCheckpointIsRebuiltFromTheJournal() throws Exception {
        JsonEconomyStore first = store();
        first.apply(batch(record(1L, transfer(1000L)), account(ALICE_KEY, 26000L)));
        first.apply(batch(record(2L, deposit(500L)), account(BOB_KEY, 25500L)));
        Files.write(checkpointPath(), "{broken".getBytes(StandardCharsets.UTF_8));

        StoreSnapshot snapshot = store().load();

        assertFalse(snapshot.readOnly());
        assertTrue(Files.exists(checkpointPath().resolveSibling(checkpointPath().getFileName() + ".broken")));
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
        JsonEconomyStore first = storeWithWindow(0L);
        first.apply(batch(record(1L, transfer(1000L)), account(ALICE_KEY, 26000L)));
        first.apply(batch(record(2L, deposit(500L)), account(BOB_KEY, 25500L)));

        StoreSnapshot state = first.load();
        assertTrue(
            first.save(state)
                .successful());

        assertEquals("", new String(Files.readAllBytes(journalPath()), StandardCharsets.UTF_8));
        StoreSnapshot after = storeWithWindow(0L).load();
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
    void checkpointIsWrittenOnlyWhenAccountsMoved() {
        JsonEconomyStore store = store();

        CheckpointResult idle = store.checkpoint(stateOf(0L));
        assertTrue(idle.successful());
        assertFalse(idle.written());

        store.apply(batch(record(1L, transfer(1000L)), account(ALICE_KEY, 26000L)));
        CheckpointResult written = store.checkpoint(stateOf(1L, account(ALICE_KEY, 26000L)));
        assertTrue(written.written());
        assertEquals(1L, written.seq(), "ответ называет границу, до которой доведён снимок");

        assertFalse(
            store.checkpoint(stateOf(1L, account(ALICE_KEY, 26000L)))
                .written());
    }

    @Test
    void unreadableJournalGoesReadOnlyAndKeepsTheEvidence() throws Exception {
        JsonEconomyStore first = store();
        first.apply(batch(record(1L, transfer(1000L)), account(ALICE_KEY, 26000L)));
        Files.delete(journalPath());
        Files.createDirectory(journalPath());

        StoreSnapshot snapshot = first.load();

        assertTrue(snapshot.readOnly());
        assertTrue(
            snapshot.reason()
                .orElse("")
                .contains("cannot be read"));
        assertTrue(Files.exists(journalPath().resolveSibling(EconomyConstants.JOURNAL_FILE + ".quarantine")));
    }

    /**
     * Хвост окна идемпотентности строится по журналу на диске. Из кольцевой истории его брать нельзя:
     * на оживлённом сервере она вытесняет операции, чьё окно ещё не вышло, и повтор доставки пакета
     * после compact списал бы деньги второй раз.
     */
    @Test
    void compactKeepsTheIdempotencyTailEvenWhenTheSnapshotForgotIt() throws Exception {
        JsonEconomyStore first = storeWithWindow(WINDOW);
        first.apply(batch(record(1L, transfer(1000L)), account(ALICE_KEY, 26000L)));
        first.apply(batch(record(2L, deposit(500L)), account(BOB_KEY, 25500L)));

        StoreSnapshot forgetful = StoreSnapshot.of(
            first.load()
                .accounts(),
            2L,
            Collections.<TransactionRecord>emptyList());
        assertTrue(
            first.save(forgetful)
                .successful());

        String[] lines = new String(Files.readAllBytes(journalPath()), StandardCharsets.UTF_8).split("\n");
        assertEquals(2, lines.length, "обе записи в окне пережили обрезку");
        assertEquals(
            2L,
            storeWithWindow(WINDOW).load()
                .lastSeq());
    }

    @Test
    void verifyComparesTheJournalTailWithCurrentAccounts() {
        JsonEconomyStore first = store();
        first.apply(batch(record(1L, transfer(1000L)), account(ALICE_KEY, 26000L)));
        first.apply(batch(record(2L, deposit(500L)), account(BOB_KEY, 25500L)));
        first.checkpoint(stateOf(2L, account(ALICE_KEY, 26000L), account(BOB_KEY, 25500L)));

        StoreSnapshot state = first.load();
        StoreVerification agreeing = first.verify(state.accounts(), state.lastSeq());
        assertTrue(
            agreeing.findings()
                .isEmpty());

        Map<UUID, AccountView> tampered = new LinkedHashMap<>(state.accounts());
        tampered.put(key(ALICE_KEY), account(ALICE_KEY, 9L));
        StoreVerification broken = first.verify(tampered, state.lastSeq());
        assertEquals(
            1,
            broken.findings()
                .size());
        assertTrue(
            broken.findings()
                .get(0)
                .contains("account holds 9"));
        assertEquals(2L, broken.settled());
    }

    /**
     * Сверка на живом сервере: игрок платит пока фоновый поток читает журнал. Запись выше границы
     * снимка это не расхождение, и называть её расхождением значит отправить администратора искать
     * несуществующую аварию.
     */
    @Test
    void verifyIgnoresRecordsWrittenAfterTheSnapshot() {
        JsonEconomyStore first = store();
        first.apply(batch(record(1L, deposit(BOB_KEY, 30000L)), account(BOB_KEY, 30000L)));
        StoreSnapshot state = first.load();

        first.apply(batch(record(2L, deposit(BOB_KEY, 40000L)), account(BOB_KEY, 40000L)));

        StoreVerification verification = first.verify(state.accounts(), state.lastSeq());

        assertTrue(
            verification.findings()
                .isEmpty(),
            "запись, пришедшая после снимка, расхождением не считается");
        assertEquals(1L, verification.ahead());
    }

    @Test
    void verifyNamesTheLinesItCouldNotRead() throws Exception {
        JsonEconomyStore first = store();
        first.apply(batch(record(1L, deposit(BOB_KEY, 30000L)), account(BOB_KEY, 30000L)));
        first.apply(batch(record(2L, deposit(CAROL_KEY, 31000L)), account(CAROL_KEY, 31000L)));
        StoreSnapshot state = first.load();
        String journal = new String(Files.readAllBytes(journalPath()), StandardCharsets.UTF_8);
        String[] lines = journal.split("\n");
        Files.write(
            journalPath(),
            (lines[0] + "\nnot a json line at all\n" + lines[1] + "\n").getBytes(StandardCharsets.UTF_8));

        StoreVerification verification = first.verify(state.accounts(), state.lastSeq());

        assertEquals(1L, verification.damaged());
        assertTrue(
            verification.findings()
                .get(0)
                .contains("unreadable line"));
    }

    private void damageTheMiddleLine() throws Exception {
        JsonEconomyStore first = store();
        first.apply(batch(record(1L, transfer(1000L)), account(ALICE_KEY, 26000L)));
        first.apply(batch(record(2L, deposit(500L)), account(BOB_KEY, 25500L)));
        String journal = new String(Files.readAllBytes(journalPath()), StandardCharsets.UTF_8);
        String[] lines = journal.split("\n");
        Files.write(
            journalPath(),
            (lines[0] + "\nnot a json line at all\n" + lines[1] + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static final String ALICE_KEY = "00000000-0000-0000-0000-0000000000a1";
    private static final String BOB_KEY = "00000000-0000-0000-0000-0000000000b2";
    private static final String CAROL_KEY = "00000000-0000-0000-0000-0000000000c3";
    private static final String DAVE_KEY = "00000000-0000-0000-0000-0000000000d4";

    private static UUID key(String value) {
        return UUID.fromString(value);
    }

    private static long balance(StoreSnapshot snapshot, String owner) {
        AccountView account = snapshot.accounts()
            .get(key(owner));
        assertNotNull(account, owner + " отсутствует в снимке");
        return account.balances()
            .get("coin")
            .longValue();
    }

    private Path checkpointPath() {
        return TestConfigs.of(root)
            .path(JsonEconomyStore.spec());
    }

    private Path journalPath() {
        return checkpointPath().getParent()
            .resolve(EconomyConstants.JOURNAL_FILE);
    }

    private JsonEconomyStore store() {
        return storeWithWindow(0L);
    }

    private JsonEconomyStore storeWithWindow(long idempotencyMillis) {
        TestConfigs files = TestConfigs.of(root);
        return new JsonEconomyStore(
            files.open(JsonEconomyStore.spec()),
            EconomyFixtures.settings()
                .ceilings(EconomyFixtures.LOG),
            JsonEconomyStore.starting(Collections.singletonList(EconomyFixtures.coin())),
            idempotencyMillis,
            () -> now,
            EconomyFixtures.LOG);
    }

    private static StoreSnapshot stateOf(long lastSeq, AccountView... accounts) {
        Map<UUID, AccountView> map = new LinkedHashMap<>();
        for (AccountView account : accounts) {
            map.put(account.uuid(), account);
        }
        return StoreSnapshot.of(map, lastSeq, Collections.<TransactionRecord>emptyList());
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
        return deposit(BOB_KEY, START + amount);
    }

    private static TransactionRecord deposit(String owner, long after) {
        return TransactionRecord.builder(TransactionRecord.Kind.DEPOSIT, "coin", "tx-deposit")
            .seq(2L)
            .ts(2000L)
            .to(key(owner), after)
            .cause(ChangeCause.COMMAND)
            .build();
    }

    private static AccountView account(String owner, long amount) {
        return AccountView.of(key(owner), owner, Collections.singletonMap("coin", Long.valueOf(amount)), false, 1000L);
    }

    /**
     * Сверка идёт в фоновом потоке, а автосейв в главном: чекпоинт может уйти дальше снимка счетов,
     * пока сверка его читает. Сравнивать новые счета со старым снимком нельзя, любое расхождение в
     * таком сравнении выдумано.
     */
    @Test
    void verifyStandsDownWhenTheCheckpointMovedPastTheSnapshot() {
        JsonEconomyStore first = store();
        first.apply(batch(record(1L, deposit(BOB_KEY, 30000L)), account(BOB_KEY, 30000L)));
        StoreSnapshot state = first.load();

        first.apply(batch(record(2L, deposit(CAROL_KEY, 31000L)), account(CAROL_KEY, 31000L)));
        first.checkpoint(stateOf(2L, account(BOB_KEY, 30000L), account(CAROL_KEY, 31000L)));

        StoreVerification verification = first.verify(state.accounts(), state.lastSeq());

        assertTrue(verification.voided());
        assertTrue(
            verification.findings()
                .get(0)
                .contains("checkpoint moved"));
    }

    /**
     * Мир привязывается к конфигам состояния мира позже, чем поднимается сервер, и до этого путь файла
     * равен null. Загрузка на слишком раннем событии раньше давала NPE и роняла старт сервера целиком,
     * поэтому здесь ждём внятный отказ с указанием нужного события.
     */
    @Test
    void journalPathRefusesUntilTheWorldIsAttached() {
        ConfigFile<JsonObject> detached = new ConfigFile<JsonObject>() {

            @Override
            public JsonObject get() {
                return new JsonObject();
            }

            @Override
            public boolean loaded() {
                return false;
            }

            @Override
            public void save() {}

            @Override
            public void reload() {}

            @Override
            public Path path() {
                return null;
            }
        };

        IllegalStateException refusal = assertThrows(
            IllegalStateException.class,
            () -> JsonEconomyStore.journalPath(detached));
        assertTrue(
            refusal.getMessage()
                .contains("FMLServerStartingEvent"));
    }
}
