package com.mrleonardos.codeeconomy.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;
import com.mrleonardos.codeeconomy.api.store.ChangeBatch;
import com.mrleonardos.codeeconomy.api.store.EconomyStore;
import com.mrleonardos.codeeconomy.api.store.StoreSnapshot;
import com.mrleonardos.codeeconomy.internal.EconomyFixtures;
import com.mrleonardos.codeeconomy.internal.TestConfigs;

/**
 * Один набор проверок для всех реализаций {@code EconomyStore}.
 *
 * <p>
 * Заводится потому, что двойник из тестов движка выполнял контракт полной выгрузки, а настоящее
 * json-хранилище его нарушало: тесты движка были зелёные, а деньги на сервере терялись. Пока обе
 * реализации не проходят одни и те же проверки, зелёный движок ничего не доказывает про носитель.
 */
class EconomyStoreContractTest {

    private static final String COIN = "coin";
    private static final UUID ALICE = EconomyFixtures.ALICE;
    private static final UUID BOB = EconomyFixtures.BOB;
    private static final UUID CAROL = EconomyFixtures.CAROL;

    @TempDir
    Path root;

    /** Носитель, который можно открыть заново: так тест изображает перезапуск сервера. */
    private interface Storage {

        EconomyStore open();
    }

    static Stream<String> implementations() {
        return Stream.of("memory", "json");
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void emptyStorageLoadsEmpty(String name) {
        StoreSnapshot snapshot = storage(name).open()
            .load();

        assertTrue(
            snapshot.accounts()
                .isEmpty());
        assertEquals(0L, snapshot.checkpointSeq());
        assertEquals(0L, snapshot.lastSeq());
    }

    @ParameterizedTest
    @MethodSource("implementations")
    void appliedRecordsSurviveAReopen(String name) {
        Storage storage = storage(name);
        EconomyStore first = storage.open();
        first.apply(batch(record(1L, ALICE, 30000L), account(ALICE, 30000L)));
        first.apply(batch(record(2L, BOB, 31000L), account(BOB, 31000L)));
        first.close();

        StoreSnapshot snapshot = storage.open()
            .load();

        assertEquals(30000L, balance(snapshot, ALICE));
        assertEquals(31000L, balance(snapshot, BOB));
        assertEquals(2L, snapshot.lastSeq());
    }

    /**
     * Смысл {@code save} в SPI это полная выгрузка. Провайдер обязан записать счета из снимка целиком,
     * а не разницу с тем, что он видел через {@code apply}: на этом стоит {@code /eco compact}, который
     * сразу после выгрузки выбрасывает всё старое.
     */
    @ParameterizedTest
    @MethodSource("implementations")
    void saveWritesTheWholeSnapshotIncludingAccountsTheStoreNeverSaw(String name) {
        Storage storage = storage(name);
        EconomyStore first = storage.open();
        first.apply(batch(record(1L, ALICE, 30000L), account(ALICE, 30000L)));

        Map<UUID, AccountView> whole = new LinkedHashMap<>();
        whole.put(ALICE, account(ALICE, 30000L));
        whole.put(BOB, account(BOB, 31000L));
        whole.put(CAROL, account(CAROL, 32000L));
        assertTrue(
            first.save(StoreSnapshot.of(whole, 7L, Collections.<TransactionRecord>emptyList()))
                .successful());
        first.close();

        StoreSnapshot snapshot = storage.open()
            .load();

        assertEquals(30000L, balance(snapshot, ALICE));
        assertEquals(31000L, balance(snapshot, BOB), "счёт из снимка попал на носитель");
        assertEquals(32000L, balance(snapshot, CAROL), "счёт из снимка попал на носитель");
        assertEquals(7L, snapshot.checkpointSeq(), "граница чекпоинта равна границе снимка");
    }

    /**
     * Тот же {@code save}, но состояние поднято из носителя после аварийного перезапуска: провайдер
     * ничего не накапливал в этой сессии, и всё равно обязан записать снимок целиком.
     */
    @ParameterizedTest
    @MethodSource("implementations")
    void saveAfterAReopenKeepsEverythingThatWasRaised(String name) {
        Storage storage = storage(name);
        EconomyStore first = storage.open();
        first.apply(batch(record(1L, ALICE, 30000L), account(ALICE, 30000L)));
        first.apply(batch(record(2L, BOB, 31000L), account(BOB, 31000L)));
        first.close();

        EconomyStore afterCrash = storage.open();
        StoreSnapshot raised = afterCrash.load();
        assertTrue(
            afterCrash.save(StoreSnapshot.of(raised.accounts(), raised.lastSeq(), raised.transactions()))
                .successful());
        afterCrash.close();

        StoreSnapshot snapshot = storage.open()
            .load();

        assertEquals(30000L, balance(snapshot, ALICE));
        assertEquals(31000L, balance(snapshot, BOB));
        assertEquals(2L, snapshot.checkpointSeq());
    }

    private Storage storage(String name) {
        if ("memory".equals(name)) {
            EconomyFixtures.MemoryStore store = new EconomyFixtures.MemoryStore();
            return () -> store;
        }
        return () -> {
            TestConfigs files = new TestConfigs(root);
            return new JsonEconomyStore(
                files.open(JsonEconomyStore.spec()),
                EconomyFixtures.settings()
                    .ceilings(EconomyFixtures.LOG),
                JsonEconomyStore.starting(Collections.singletonList(EconomyFixtures.coin())),
                0L,
                () -> 0L,
                EconomyFixtures.LOG);
        };
    }

    private static long balance(StoreSnapshot snapshot, UUID player) {
        AccountView account = snapshot.accounts()
            .get(player);
        assertNotNull(account, player + " отсутствует на носителе");
        return account.balances()
            .get(COIN)
            .longValue();
    }

    private static ChangeBatch batch(TransactionRecord record, AccountView account) {
        return ChangeBatch.builder(ChangeCause.COMMAND)
            .upsert(account)
            .append(record)
            .build();
    }

    private static TransactionRecord record(long seq, UUID target, long after) {
        return TransactionRecord.builder(TransactionRecord.Kind.DEPOSIT, COIN, "tx" + seq)
            .seq(seq)
            .ts(seq * 1000L)
            .to(target, after)
            .cause(ChangeCause.COMMAND)
            .build();
    }

    private static AccountView account(UUID player, long amount) {
        return AccountView
            .of(player, player.toString(), Collections.singletonMap(COIN, Long.valueOf(amount)), false, 1000L);
    }
}
