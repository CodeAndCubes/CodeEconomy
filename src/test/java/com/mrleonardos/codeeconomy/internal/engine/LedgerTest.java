package com.mrleonardos.codeeconomy.internal.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codeeconomy.api.CurrencyIds;
import com.mrleonardos.codeeconomy.api.event.DegradedEvent;
import com.mrleonardos.codeeconomy.api.event.EconomyListener;
import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.api.model.ResultCode;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;
import com.mrleonardos.codeeconomy.api.model.TransferRequest;
import com.mrleonardos.codeeconomy.api.model.TransferResult;
import com.mrleonardos.codeeconomy.api.store.CheckpointResult;
import com.mrleonardos.codeeconomy.api.store.StoreResult;
import com.mrleonardos.codeeconomy.api.store.StoreSnapshot;
import com.mrleonardos.codeeconomy.api.store.StoreVerification;
import com.mrleonardos.codeeconomy.internal.EconomyConfig;
import com.mrleonardos.codeeconomy.internal.EconomyFixtures;
import com.mrleonardos.codeeconomy.internal.EconomyNodes;
import com.mrleonardos.codeeconomy.internal.RecordingLogger;
import com.mrleonardos.codeeconomy.internal.service.PlayerLookup;

class LedgerTest {

    private static final String COIN = CurrencyIds.DEFAULT;
    private static final String CREDIT = "credit";
    private static final long START = 25000L;

    private final AtomicLong now = new AtomicLong(1000L);
    private final EconomyFixtures.MemoryStore store = new EconomyFixtures.MemoryStore();

    @Test
    void insufficientRefusalLeavesAccountsAndJournalUntouched() {
        Ledger ledger = coinLedger(
            account(COIN, EconomyFixtures.ALICE, 100L),
            account(COIN, EconomyFixtures.BOB, 100L));

        TransferResult result = ledger.execute(
            EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 150L, "tx1"),
            ChangeCause.COMMAND);

        assertEquals(ResultCode.INSUFFICIENT, result.code());
        assertEquals(100L, balance(ledger, EconomyFixtures.ALICE));
        assertEquals(100L, balance(ledger, EconomyFixtures.BOB));
        assertTrue(store.applied.isEmpty(), "отказ до записи журнала не доходит");
    }

    @Test
    void recipientAboveTheCeilingKeepsTheSenderUnchanged() {
        long ceiling = EconomyFixtures.coin()
            .maxBalance();
        Ledger ledger = coinLedger(
            account(COIN, EconomyFixtures.ALICE, 1000L),
            account(COIN, EconomyFixtures.BOB, ceiling));

        TransferResult result = ledger.execute(
            EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 100L, "tx1"),
            ChangeCause.COMMAND);

        assertEquals(ResultCode.ABOVE_CEILING, result.code());
        assertEquals(1000L, balance(ledger, EconomyFixtures.ALICE));
        assertEquals(ceiling, balance(ledger, EconomyFixtures.BOB));
        assertTrue(store.applied.isEmpty());
    }

    @Test
    void transferToSelfIsRefused() {
        Ledger ledger = coinLedger(account(COIN, EconomyFixtures.ALICE, 1000L));

        TransferResult result = ledger.execute(
            EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.ALICE, 10L, "tx1"),
            ChangeCause.COMMAND);

        assertEquals(ResultCode.SAME_ACCOUNT, result.code());
        assertEquals(1000L, balance(ledger, EconomyFixtures.ALICE));
        assertTrue(store.applied.isEmpty());
    }

    @Test
    void unfittingAmountIsRefusedBeforeTheSides() {
        Ledger ledger = coinLedger(
            account(COIN, EconomyFixtures.ALICE, 1000L),
            account(COIN, EconomyFixtures.BOB, 1000L));

        assertEquals(
            ResultCode.BAD_AMOUNT,
            ledger
                .execute(
                    EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 0L, "tx1"),
                    ChangeCause.COMMAND)
                .code());
        assertEquals(
            ResultCode.BAD_AMOUNT,
            ledger
                .execute(
                    EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, -5L, "tx2"),
                    ChangeCause.COMMAND)
                .code());
        assertEquals(
            ResultCode.BAD_AMOUNT,
            ledger.execute(EconomyFixtures.deposit(EconomyFixtures.BOB, 0L, "tx3"), ChangeCause.COMMAND)
                .code());
        assertTrue(store.applied.isEmpty());
    }

    @Test
    void resetReturnsTheStartingBalance() {
        Ledger ledger = coinLedger(account(COIN, EconomyFixtures.ALICE, 1000L));

        TransferResult result = ledger
            .execute(EconomyFixtures.reset(EconomyFixtures.ALICE, "tx1"), ChangeCause.COMMAND);

        assertEquals(ResultCode.OK, result.code());
        assertEquals(
            START,
            result.toAfter()
                .getAsLong());
        assertEquals(1, store.applied.size());
    }

    @Test
    void setTakesAnyAmountDownToTheNegativeFloor() {
        Ledger ledger = creditLedger(account(CREDIT, EconomyFixtures.ALICE, 100L));

        assertEquals(
            ResultCode.OK,
            ledger.execute(set(EconomyFixtures.ALICE, 0L, "tx1"), ChangeCause.COMMAND, true)
                .code());
        assertEquals(
            ResultCode.OK,
            ledger.execute(set(EconomyFixtures.ALICE, -500L, "tx2"), ChangeCause.COMMAND, true)
                .code());
        assertEquals(-500L, balance(ledger, CREDIT, EconomyFixtures.ALICE));
        assertEquals(
            ResultCode.BELOW_FLOOR,
            ledger.execute(set(EconomyFixtures.ALICE, -501L, "tx3"), ChangeCause.COMMAND, true)
                .code());
        assertEquals(2, store.applied.size(), "ниже пола запись не появляется");
    }

    /**
     * Находка про пустой actor: пол ниже нуля отдавался всякому, кто не заполнил поле автора, а его
     * ставит любой вызывающий. Чужой мод уводил игрока в кредит, не имея ни одной ноды.
     */
    @Test
    void aRequestWithoutAnActorGetsNoFloorBypass() {
        Ledger ledger = creditLedger(account(CREDIT, EconomyFixtures.ALICE, 100L));

        TransferResult result = ledger.execute(
            TransferRequest.withdraw(EconomyFixtures.ALICE, 200L, CREDIT, "tx1", null, "foreign mod"),
            ChangeCause.API);

        assertEquals(ResultCode.BELOW_FLOOR, result.code());
        assertEquals(100L, balance(ledger, CREDIT, EconomyFixtures.ALICE));
        assertTrue(store.applied.isEmpty(), "до записи дело не доходит");
    }

    @Test
    void setWithoutBypassStaysAboveTheFloor() {
        Ledger ledger = creditLedger(account(CREDIT, EconomyFixtures.ALICE, 100L));

        TransferResult result = ledger.execute(setByPlayer(EconomyFixtures.ALICE, -1L, "tx1"), ChangeCause.COMMAND);

        assertEquals(ResultCode.BELOW_FLOOR, result.code());
        assertEquals(100L, balance(ledger, CREDIT, EconomyFixtures.ALICE));
    }

    @Test
    void unknownPlayerIsRefusedWithoutCreatingAnAccount() {
        Ledger ledger = coinLedger(account(COIN, EconomyFixtures.ALICE, 1000L));

        assertEquals(
            ResultCode.UNKNOWN_PLAYER,
            ledger
                .execute(
                    EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.CAROL, 10L, "tx1"),
                    ChangeCause.COMMAND)
                .code());
        assertEquals(
            ResultCode.UNKNOWN_PLAYER,
            ledger
                .execute(
                    EconomyFixtures.transfer(EconomyFixtures.CAROL, EconomyFixtures.ALICE, 10L, "tx2"),
                    ChangeCause.COMMAND)
                .code());
        assertTrue(store.applied.isEmpty());
        assertFalse(
            ledger.state()
                .account(EconomyFixtures.CAROL)
                .isPresent(),
            "незнакомый счёт не создаётся");
    }

    /**
     * Порядок шагов конвейера задан дизайном: валюта разбирается до суммы. Запрос, негодный сразу по
     * обоим, обязан ответить именно про валюту, иначе перестановка шагов пройдёт незамеченной.
     */
    @Test
    void unknownCurrencyIsAnsweredBeforeTheAmount() {
        Ledger ledger = coinLedger(account(COIN, EconomyFixtures.ALICE, 1000L));

        TransferResult result = ledger.execute(
            TransferRequest.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 0L, "nope", "tx1", null, null),
            ChangeCause.COMMAND);

        assertEquals(ResultCode.UNKNOWN_CURRENCY, result.code());
        assertTrue(store.applied.isEmpty());
    }

    @Test
    void oversizedReasonIsAnInvalidRequest() {
        Ledger ledger = coinLedger(
            account(COIN, EconomyFixtures.ALICE, 1000L),
            account(COIN, EconomyFixtures.BOB, 1000L));

        TransferResult result = ledger.execute(
            TransferRequest
                .transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 10L, COIN, "tx1", null, times('r', 200)),
            ChangeCause.COMMAND);

        assertEquals(ResultCode.INVALID_REQUEST, result.code());
        assertTrue(store.applied.isEmpty());
    }

    @Test
    void frozenAccountIsClosedInBothDirections() {
        Ledger ledger = coinLedger(
            account(COIN, EconomyFixtures.ALICE, 1000L),
            account(COIN, EconomyFixtures.BOB, 1000L));
        assertTrue(
            ledger.setFrozen(EconomyFixtures.ALICE, true)
                .successful());

        assertEquals(
            ResultCode.ACCOUNT_FROZEN,
            ledger
                .execute(
                    EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 10L, "tx1"),
                    ChangeCause.COMMAND)
                .code());
        assertEquals(
            ResultCode.ACCOUNT_FROZEN,
            ledger
                .execute(
                    EconomyFixtures.transfer(EconomyFixtures.BOB, EconomyFixtures.ALICE, 10L, "tx2"),
                    ChangeCause.COMMAND)
                .code());
        assertEquals(1000L, balance(ledger, EconomyFixtures.ALICE));

        assertTrue(
            ledger.setFrozen(EconomyFixtures.ALICE, false)
                .successful());
        assertEquals(
            ResultCode.OK,
            ledger
                .execute(
                    EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 10L, "tx3"),
                    ChangeCause.COMMAND)
                .code());
    }

    @Test
    void repeatAnswersWithTheRecordedOutcome() {
        Ledger ledger = coinLedger(
            account(COIN, EconomyFixtures.ALICE, 1000L),
            account(COIN, EconomyFixtures.BOB, 1000L));

        TransferResult first = ledger.execute(
            EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 300L, "shop:42"),
            ChangeCause.COMMAND);
        TransferResult second = ledger.execute(
            EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 300L, "shop:42"),
            ChangeCause.COMMAND);

        assertEquals(ResultCode.OK, first.code());
        assertEquals(ResultCode.DUPLICATE, second.code());
        assertEquals(first.toAfter(), second.toAfter());
        assertEquals(700L, balance(ledger, EconomyFixtures.ALICE));
        assertEquals(1, store.applied.size(), "деньги списаны один раз");
    }

    @Test
    void duplicateIsCaughtAfterTheEngineIsRebuilt() {
        Ledger first = coinLedger(
            account(COIN, EconomyFixtures.ALICE, 1000L),
            account(COIN, EconomyFixtures.BOB, 1000L));
        first.execute(
            EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 300L, "shop:42"),
            ChangeCause.COMMAND);

        Ledger restarted = rebuilt();
        TransferResult repeat = restarted.execute(
            EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 300L, "shop:42"),
            ChangeCause.COMMAND);

        assertEquals(ResultCode.DUPLICATE, repeat.code());
        assertEquals(1300L, balance(restarted, EconomyFixtures.BOB));
        assertEquals(1, store.applied.size(), "после рестарта повтор второй раз не применяется");
    }

    @Test
    void repeatOutsideTheWindowIsAppliedWithAWarning() {
        EconomyFixtures.Configs config = EconomyFixtures.configs();
        config.settings.history.idempotencyHours = 1;
        RecordingLogger log = new RecordingLogger();
        Ledger ledger = coinLedger(config.build(), EconomyFixtures.lookup(), log);

        ledger.execute(
            EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 300L, "shop:42"),
            ChangeCause.COMMAND);
        now.addAndGet(2L * 3600L * 1000L);
        TransferResult repeat = ledger.execute(
            EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 300L, "shop:42"),
            ChangeCause.COMMAND);

        assertEquals(ResultCode.OK, repeat.code(), "за окном повтор считается новой операцией");
        assertTrue(log.anyWarnContains("outside the idempotency window"), "но с предупреждением в логе");
    }

    @Test
    void refusedApplyEntersDegradationAndKeepsTheSnapshot() {
        Ledger ledger = coinLedger(
            account(COIN, EconomyFixtures.ALICE, 1000L),
            account(COIN, EconomyFixtures.BOB, 1000L));
        store.refuse = true;

        TransferResult result = ledger.execute(
            EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 300L, "tx1"),
            ChangeCause.COMMAND);

        assertEquals(ResultCode.STORE_FAILURE, result.code());
        assertTrue(ledger.degraded());
        assertEquals(1000L, balance(ledger, EconomyFixtures.ALICE));
        assertEquals(1000L, balance(ledger, EconomyFixtures.BOB));
        assertEquals(
            0L,
            ledger.state()
                .revision());
        assertTrue(store.applied.isEmpty());
    }

    @Test
    void thrownApplyIsAnsweredAsStoreFailure() {
        Ledger ledger = coinLedger(account(COIN, EconomyFixtures.ALICE, 1000L));
        store.throwOnApply = true;

        assertEquals(
            ResultCode.STORE_FAILURE,
            ledger.execute(EconomyFixtures.deposit(EconomyFixtures.ALICE, 10L, "tx1"), ChangeCause.COMMAND)
                .code());
        assertTrue(ledger.degraded());
    }

    @Test
    void readsWorkWhileDegraded() {
        Ledger ledger = coinLedger(account(COIN, EconomyFixtures.ALICE, 1000L));
        store.refuse = true;

        ledger.execute(EconomyFixtures.deposit(EconomyFixtures.ALICE, 10L, "tx1"), ChangeCause.COMMAND);

        assertEquals(1000L, ledger.balance(EconomyFixtures.ALICE, COIN));
        assertTrue(ledger.has(EconomyFixtures.ALICE, 1000L, COIN));
        assertEquals(
            EconomyFixtures.ALICE,
            ledger.top(COIN, 0, 10, now.get())
                .get(0)
                .player(),
            "топ читается по последнему записанному состоянию");
    }

    @Test
    void degradedEventComesOnEntryAndOnExit() {
        Ledger ledger = coinLedger(account(COIN, EconomyFixtures.ALICE, 1000L));
        List<Boolean> degraded = new java.util.ArrayList<>();
        ledger.events()
            .register(0, new EconomyListener() {

                @Override
                public void onDegraded(DegradedEvent event) {
                    degraded.add(Boolean.valueOf(event.degraded()));
                }
            });

        store.refuse = true;
        ledger.execute(EconomyFixtures.deposit(EconomyFixtures.ALICE, 10L, "tx1"), ChangeCause.COMMAND);
        store.refuse = false;
        ledger.execute(EconomyFixtures.deposit(EconomyFixtures.ALICE, 10L, "tx2"), ChangeCause.COMMAND);

        assertEquals(Arrays.asList(Boolean.TRUE, Boolean.FALSE), degraded);
        assertFalse(ledger.degraded());
        assertEquals(1010L, balance(ledger, EconomyFixtures.ALICE));
    }

    @Test
    void importWritesOneRecordPerAccountAndARepeatChangesNothing() {
        Ledger ledger = coinLedger(account(COIN, EconomyFixtures.ALICE, 1000L));

        assertEquals(
            ResultCode.OK,
            ledger.execute(importRequest(EconomyFixtures.ALICE, 4000L), ChangeCause.MIGRATION)
                .code());
        assertEquals(
            ResultCode.OK,
            ledger.execute(importRequest(EconomyFixtures.BOB, 700L), ChangeCause.MIGRATION)
                .code());
        assertEquals(
            ResultCode.DUPLICATE,
            ledger.execute(importRequest(EconomyFixtures.ALICE, 4000L), ChangeCause.MIGRATION)
                .code());

        assertEquals(4000L, balance(ledger, EconomyFixtures.ALICE));
        assertEquals(700L, balance(ledger, EconomyFixtures.BOB));
        assertEquals(2, store.applied.size(), "повторный импорт новых записей журнала не создаёт");
    }

    @Test
    void auditLineCarriesCauseActorAndTransactionId() {
        RecordingLogger log = new RecordingLogger();
        Ledger ledger = coinLedger(EconomyFixtures.config(), EconomyFixtures.lookup(), log);

        ledger.execute(
            TransferRequest.transfer(
                EconomyFixtures.ALICE,
                EconomyFixtures.BOB,
                300L,
                COIN,
                "shop:42",
                EconomyFixtures.ALICE,
                "order"),
            ChangeCause.COMMAND);

        assertTrue(log.anyInfoContains("cause COMMAND"));
        assertTrue(log.anyInfoContains("actor " + EconomyFixtures.ALICE));
        assertTrue(log.anyInfoContains("tx shop:42"));
        assertTrue(log.anyInfoContains("reason order"));
    }

    @Test
    void auditCanBeSilenced() {
        EconomyFixtures.Configs config = EconomyFixtures.configs();
        config.logChanges = false;
        RecordingLogger log = new RecordingLogger();
        Ledger ledger = coinLedger(config.build(), EconomyFixtures.lookup(), log);

        ledger.execute(
            EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 300L, "tx1"),
            ChangeCause.COMMAND);

        assertFalse(log.anyInfoContains("cause COMMAND"), "выключенный аудит строк изменений не пишет");
    }

    @Test
    void refusalsAreExplainedOnlyWithLogChecks() {
        RecordingLogger silent = new RecordingLogger();
        Ledger withoutChecks = coinLedger(EconomyFixtures.config(), EconomyFixtures.lookup(), silent);
        withoutChecks.execute(
            EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 10L, "tx1"),
            ChangeCause.COMMAND);

        assertFalse(silent.anyDebugContains("is refused with"), "по умолчанию отказы на уровне debug не пишутся");

        EconomyFixtures.Configs config = EconomyFixtures.configs();
        config.logChecks = true;
        RecordingLogger loud = new RecordingLogger();
        Ledger withChecks = coinLedger(config.build(), EconomyFixtures.lookup(), loud);
        withChecks.execute(
            EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 30000L, "tx2"),
            ChangeCause.COMMAND);

        assertTrue(loud.anyDebugContains("INSUFFICIENT"));
    }

    @Test
    void startingMetaReplacesTheStartBalance() {
        RecordingLogger log = new RecordingLogger();
        Ledger ledger = coinLedger(meta(EconomyNodes.META_STARTING, "30"), log);

        TransferResult reset = ledger.execute(EconomyFixtures.reset(EconomyFixtures.ALICE, "tx1"), ChangeCause.COMMAND);

        assertEquals(ResultCode.OK, reset.code());
        assertEquals(
            3000L,
            reset.toAfter()
                .getAsLong(),
            "30 мажорных единиц это 3000 минорных");
        assertEquals(3000L, balance(ledger, EconomyFixtures.ALICE));
    }

    @Test
    void unusableStartingMetaFallsBackWithAWarning() {
        RecordingLogger letters = new RecordingLogger();
        Ledger withLetters = coinLedger(meta(EconomyNodes.META_STARTING, "much"), letters);
        withLetters.execute(EconomyFixtures.reset(EconomyFixtures.ALICE, "tx1"), ChangeCause.COMMAND);

        assertEquals(START, balance(withLetters, EconomyFixtures.ALICE));
        assertTrue(letters.anyWarnContains(EconomyNodes.META_STARTING));

        RecordingLogger zero = new RecordingLogger();
        Ledger withZero = coinLedger(meta(EconomyNodes.META_STARTING, "0"), zero);
        withZero.execute(EconomyFixtures.reset(EconomyFixtures.ALICE, "tx2"), ChangeCause.COMMAND);

        assertEquals(START, balance(withZero, EconomyFixtures.ALICE), "нулевая мета читается как отсутствие");
        assertTrue(zero.anyWarnContains(EconomyNodes.META_STARTING));
    }

    @Test
    void payLimitMetaCapsOneTransfer() {
        Ledger ledger = coinLedger(meta(EconomyNodes.META_PAY_LIMIT, "50"), new RecordingLogger());

        assertEquals(
            ResultCode.ABOVE_CEILING,
            ledger
                .execute(
                    EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 5001L, "tx1"),
                    ChangeCause.COMMAND)
                .code());
        assertEquals(
            ResultCode.OK,
            ledger
                .execute(
                    EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 4000L, "tx2"),
                    ChangeCause.COMMAND)
                .code());
    }

    @Test
    void unusablePayLimitMetaFallsBackToTheConfiguredCeiling() {
        RecordingLogger log = new RecordingLogger();
        Ledger ledger = coinLedger(meta(EconomyNodes.META_PAY_LIMIT, "much"), log);

        assertEquals(
            ResultCode.OK,
            ledger
                .execute(
                    EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 5001L, "tx1"),
                    ChangeCause.COMMAND)
                .code());
        assertTrue(log.anyWarnContains(EconomyNodes.META_PAY_LIMIT));
    }

    @Test
    void bypassNodeAllowsGoingDownToTheNegativeFloor() {
        Ledger withoutNode = creditLedger(EconomyFixtures.lookup());
        assertEquals(
            ResultCode.BELOW_FLOOR,
            withoutNode
                .execute(
                    TransferRequest.withdraw(EconomyFixtures.ALICE, 300L, CREDIT, "tx1", EconomyFixtures.ALICE, null),
                    ChangeCause.COMMAND)
                .code());

        RecordingLogger log = new RecordingLogger();
        Ledger withNode = creditLedger(lookupWithNode(), log);
        TransferResult allowed = withNode.execute(
            TransferRequest.withdraw(EconomyFixtures.ALICE, 300L, CREDIT, "tx2", EconomyFixtures.ALICE, null),
            ChangeCause.COMMAND);

        assertEquals(ResultCode.OK, allowed.code());
        assertEquals(
            -200L,
            allowed.fromAfter()
                .getAsLong());
    }

    /**
     * Правило отсутствия одно на весь мод: неизвестная валюта отвечает ноль, отсутствующий счёт или
     * валюта, которой счёт не касался, отвечает стартовый баланс. Код, javadoc {@code AccountView} и
     * восстановление журнала говорят об этом одно и то же.
     */
    @Test
    void balanceAnswersByOneAbsenceRule() {
        store.snapshot = StoreSnapshot.of(
            Collections.singletonMap(EconomyFixtures.ALICE, account(COIN, EconomyFixtures.ALICE, 300L)),
            0L,
            Collections.<TransactionRecord>emptyList());
        Ledger ledger = assembled(
            EconomyFixtures.currencies(EconomyFixtures.coin(), EconomyFixtures.credit()),
            EconomyFixtures.config(),
            EconomyFixtures.lookup(),
            EconomyFixtures.LOG);

        assertEquals(0L, balance(ledger, "doubloon", EconomyFixtures.ALICE), "неизвестная валюта отвечает ноль");
        assertEquals(
            START,
            balance(ledger, EconomyFixtures.BOB),
            "отсутствующий счёт отвечает стартовый баланс валюты");
        assertEquals(
            100L,
            balance(ledger, CREDIT, EconomyFixtures.ALICE),
            "валюта, которой счёт не касался, отвечает стартовый баланс");
        assertEquals(300L, balance(ledger, EconomyFixtures.ALICE), "записанный баланс отвечает как есть");
    }

    private Ledger coinLedger(AccountView... accounts) {
        Map<UUID, AccountView> state = new LinkedHashMap<>();
        for (AccountView account : accounts) {
            state.put(account.uuid(), account);
        }
        store.snapshot = StoreSnapshot.of(state, 0L, Collections.<TransactionRecord>emptyList());
        return assembled(
            EconomyFixtures.currencies(EconomyFixtures.coin()),
            EconomyFixtures.config(),
            EconomyFixtures.lookup(),
            EconomyFixtures.LOG);
    }

    private Ledger coinLedger(EconomyConfig config, PlayerLookup lookup, RecordingLogger log) {
        return assembled(EconomyFixtures.currencies(EconomyFixtures.coin()), config, lookup, log.logger());
    }

    private Ledger coinLedger(Map<UUID, Map<String, String>> meta, RecordingLogger log) {
        return coinLedger(EconomyFixtures.config(), EconomyFixtures.lookupWithMeta(meta), log);
    }

    private Ledger creditLedger(PlayerLookup lookup) {
        return creditLedger(lookup, EconomyFixtures.LOG);
    }

    private Ledger creditLedger(PlayerLookup lookup, Logger log) {
        store.snapshot = StoreSnapshot.of(
            Collections.singletonMap(EconomyFixtures.ALICE, account(CREDIT, EconomyFixtures.ALICE, 100L)),
            0L,
            Collections.<TransactionRecord>emptyList());
        return assembled(EconomyFixtures.currencies(EconomyFixtures.credit()), EconomyFixtures.config(), lookup, log);
    }

    private Ledger creditLedger(PlayerLookup lookup, RecordingLogger log) {
        return creditLedger(lookup, log.logger());
    }

    private Ledger creditLedger(AccountView alice) {
        store.snapshot = StoreSnapshot
            .of(Collections.singletonMap(alice.uuid(), alice), 0L, Collections.<TransactionRecord>emptyList());
        return EconomyFixtures.ledger(
            store,
            EconomyFixtures.currencies(EconomyFixtures.credit()),
            EconomyFixtures.config(),
            EconomyFixtures.lookup(),
            now::get,
            EconomyFixtures.LOG);
    }

    private Ledger rebuilt() {
        return assembled(
            EconomyFixtures.currencies(EconomyFixtures.coin()),
            EconomyFixtures.config(),
            EconomyFixtures.lookup(),
            EconomyFixtures.LOG);
    }

    private Ledger assembled(List<CurrencyRecord> currencies, EconomyConfig config, PlayerLookup lookup, Logger log) {
        return EconomyFixtures.ledger(store, currencies, config, lookup, now::get, log);
    }

    private static PlayerLookup lookupWithNode() {
        return new PlayerLookup() {

            @Override
            public String name(UUID player) {
                return player.equals(EconomyFixtures.ALICE) ? "Alice"
                    : player.equals(EconomyFixtures.BOB) ? "Bob" : null;
            }

            @Override
            public boolean has(UUID player, String node) {
                return EconomyNodes.BYPASS_MIN_BALANCE.equals(node);
            }

            @Override
            public Optional<String> meta(UUID player, String key) {
                return Optional.empty();
            }
        };
    }

    private static Map<UUID, Map<String, String>> meta(String key, String value) {
        Map<UUID, Map<String, String>> meta = new LinkedHashMap<>();
        meta.put(EconomyFixtures.ALICE, Collections.singletonMap(key, value));
        return meta;
    }

    private static TransferRequest set(UUID target, long amount, String transactionId) {
        return TransferRequest.set(target, amount, CREDIT, transactionId, null, "test");
    }

    private static TransferRequest setByPlayer(UUID target, long amount, String transactionId) {
        return TransferRequest.set(target, amount, CREDIT, transactionId, target, "test");
    }

    private static TransferRequest importRequest(UUID target, long amount) {
        return TransferRequest.set(target, amount, COIN, "import:" + COIN + ":" + target, null, "imported flatjson");
    }

    private static AccountView account(String currencyId, UUID player, long amount) {
        return AccountView.of(player, null, Collections.singletonMap(currencyId, Long.valueOf(amount)), false, 1000L);
    }

    private static long balance(Ledger ledger, UUID player) {
        return balance(ledger, COIN, player);
    }

    private static long balance(Ledger ledger, String currencyId, UUID player) {
        return ledger.balance(player, currencyId);
    }

    private static String times(char symbol, int count) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < count; index++) {
            text.append(symbol);
        }
        return text.toString();
    }

    /**
     * Провайдер без обслуживания сам держит состояние в одном месте: чекпоинт для него это полная
     * выгрузка через save. Раньше движок ходил во встроенный json мимо активного провайдера и отвечал
     * успехом, не спросив его ни разу.
     */
    @Test
    void checkpointOnAProviderWithoutMaintenanceGoesThroughSave() {
        Ledger ledger = coinLedger(account(COIN, EconomyFixtures.ALICE, 1000L));
        ledger.execute(EconomyFixtures.deposit(EconomyFixtures.ALICE, 100L, "tx1"), ChangeCause.COMMAND);

        CheckpointResult written = ledger.checkpoint();

        assertTrue(written.successful());
        assertTrue(written.written());
        assertEquals(1, store.saved, "снимок ушёл активному провайдеру");
        assertEquals(
            ledger.state()
                .lastSeq(),
            written.seq());
    }

    @Test
    void checkpointReportsTheRefusalOfAForeignProvider() {
        Ledger ledger = coinLedger(account(COIN, EconomyFixtures.ALICE, 1000L));
        ledger.execute(EconomyFixtures.deposit(EconomyFixtures.ALICE, 100L, "tx1"), ChangeCause.COMMAND);
        store.refuseSave = true;

        CheckpointResult written = ledger.checkpoint();

        assertFalse(written.successful(), "отказ провайдера не подменяется успехом");
        assertEquals(
            StoreResult.Failure.UNSUPPORTED,
            written.result()
                .failure()
                .get());
    }

    /** Провайдеру, который фиксирует операцию в apply, автосейву сохранять нечего. */
    @Test
    void autosaveAsksNothingOfAProviderWithoutMaintenance() {
        Ledger ledger = coinLedger(account(COIN, EconomyFixtures.ALICE, 1000L));
        ledger.execute(EconomyFixtures.deposit(EconomyFixtures.ALICE, 100L, "tx1"), ChangeCause.COMMAND);

        ledger.autosave();

        assertEquals(0, store.saved);
    }

    /**
     * Сверка для провайдера без обслуживания идёт по кольцевой истории в памяти. Эта ветка была
     * недостижима, пока движок спрашивал встроенный json вместо активного провайдера.
     */
    @Test
    void verifyOnAProviderWithoutMaintenanceReplaysTheRingHistory() {
        Ledger ledger = coinLedger();
        ledger.execute(EconomyFixtures.deposit(EconomyFixtures.ALICE, 100L, "tx1"), ChangeCause.COMMAND);
        ledger.execute(
            EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 100L, "tx2"),
            ChangeCause.COMMAND);

        StoreVerification verification = ledger.verifyDetailed();

        assertFalse(verification.voided(), "вся история в памяти, сверять есть что");
        assertTrue(
            verification.findings()
                .isEmpty(),
            "журнал и балансы сходятся");
    }

    /**
     * История в памяти обрезана, а журнала у провайдера нет: сверять нечем. Пустой отчёт администратор
     * прочтёт как «всё сошлось», поэтому сверка честно говорит, что не состоялась.
     */
    @Test
    void verifyWithoutAJournalAndWithoutAFullHistorySaysSo() {
        EconomyFixtures.Configs config = EconomyFixtures.configs();
        config.settings.history.maxEntries = 1;
        Ledger ledger = assembled(
            EconomyFixtures.currencies(EconomyFixtures.coin()),
            config.build(),
            EconomyFixtures.lookup(),
            EconomyFixtures.LOG);
        ledger.execute(EconomyFixtures.deposit(EconomyFixtures.ALICE, 100L, "tx1"), ChangeCause.COMMAND);

        StoreVerification verification = ledger.verifyDetailed();

        assertTrue(verification.voided());
        assertTrue(
            verification.findings()
                .get(0)
                .contains("nothing to compare"));
    }
}
