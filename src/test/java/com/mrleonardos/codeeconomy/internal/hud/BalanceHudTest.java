package com.mrleonardos.codeeconomy.internal.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codeeconomy.api.CurrencyIds;
import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.TransferRequest;
import com.mrleonardos.codeeconomy.internal.EconomyFixtures;
import com.mrleonardos.codeeconomy.internal.EconomyNodes;
import com.mrleonardos.codeeconomy.internal.TestConfigs;
import com.mrleonardos.codeeconomy.internal.event.EventDispatcher;
import com.mrleonardos.codeeconomy.internal.service.LedgerService;
import com.mrleonardos.codeeconomy.internal.service.PlayerLookup;
import com.mrleonardos.codeeconomy.internal.store.JsonEconomyStore;

/**
 * Кому и когда уходит показ баланса.
 *
 * <p>
 * Проверка стоит на уровне отправки: между движком и пакетом сидит записывающий сток, поэтому видно
 * ровно то, что ушло бы в сеть, и запускать Minecraft для этого не нужно.
 */
class BalanceHudTest {

    private static final String COIN = CurrencyIds.DEFAULT;
    private static final String CREDIT = "credit";
    private static final long COIN_START = 25000L;
    private static final long CREDIT_START = 100L;

    @TempDir
    Path root;

    private final RecordingSink sent = new RecordingSink();
    private final Set<UUID> withBalanceNode = new HashSet<>();

    private LedgerService service;
    private BalanceHud hud;

    @BeforeEach
    void setUp() {
        withBalanceNode.add(EconomyFixtures.ALICE);
        withBalanceNode.add(EconomyFixtures.BOB);

        EconomyFixtures.Configs config = EconomyFixtures.configs();
        config.provider = "builtin-under-test";
        EventDispatcher events = new EventDispatcher(EconomyFixtures.LOG);
        PlayerLookup lookup = lookup();
        service = LedgerService.create(
            config.build(),
            Arrays.asList(EconomyFixtures.coin(), EconomyFixtures.credit()),
            TestConfigs.of(root)
                .open(JsonEconomyStore.spec()),
            EconomyFixtures.inlineScheduler(),
            () -> true,
            () -> 0L,
            () -> 1000L,
            lookup,
            events,
            EconomyFixtures.LOG);
        hud = new BalanceHud(service, lookup, sent, EconomyFixtures.LOG);
        events.register(0, hud);
    }

    @Test
    void aPlayerWithoutTheNodeSeesNothing() {
        withBalanceNode.remove(EconomyFixtures.ALICE);

        hud.watch(EconomyFixtures.ALICE, COIN);

        assertTrue(
            sent.lines.isEmpty(),
            "показ отвечает на тот же вопрос, что /balance, и без codeeconomy.balance молчит так же");
    }

    @Test
    void takingTheNodeAwayStopsTheUpdates() {
        watching(EconomyFixtures.ALICE, COIN);
        withBalanceNode.remove(EconomyFixtures.ALICE);

        deposit(EconomyFixtures.ALICE, 500L, COIN, "tx1");
        service.tick();

        assertTrue(sent.lines.isEmpty(), "снятая нода гасит показ без перезахода");
    }

    @Test
    void givingTheNodeBackShowsTheCurrentNumber() {
        watching(EconomyFixtures.ALICE, COIN);
        withBalanceNode.remove(EconomyFixtures.ALICE);
        deposit(EconomyFixtures.ALICE, 500L, COIN, "tx1");
        service.tick();
        withBalanceNode.add(EconomyFixtures.ALICE);

        deposit(EconomyFixtures.ALICE, 300L, COIN, "tx2");
        service.tick();

        assertEquals(
            Collections.singletonList(line(EconomyFixtures.ALICE, COIN, COIN_START + 800L, 2)),
            sent.lines,
            "отказ по праву не запоминается как отправленная сумма, иначе экран остался бы с прежним числом");
    }

    @Test
    void aFailingPermissionServiceLeavesTheDisplayOff() {
        BalanceHud broken = new BalanceHud(service, brokenLookup(), sent, EconomyFixtures.LOG);

        broken.watch(EconomyFixtures.ALICE, COIN);

        assertTrue(sent.lines.isEmpty(), "право не спросить, значит показывать нельзя");
    }

    @Test
    void aPlayerWhoDidNotAskGetsNothing() {
        deposit(EconomyFixtures.ALICE, 500L, COIN, "tx1");
        service.tick();

        assertTrue(sent.lines.isEmpty(), "игроку без просьбы пакеты не шлются: у него может не быть клиентской части");
    }

    @Test
    void theRequestIsAnsweredWithTheCurrentBalance() {
        hud.watch(EconomyFixtures.ALICE, "");

        assertEquals(Collections.singletonList(line(EconomyFixtures.ALICE, COIN, COIN_START, 2)), sent.lines);
    }

    @Test
    void aChangeReachesTheWatcher() {
        watching(EconomyFixtures.ALICE, COIN);

        deposit(EconomyFixtures.ALICE, 500L, COIN, "tx1");
        service.tick();

        assertEquals(Collections.singletonList(line(EconomyFixtures.ALICE, COIN, COIN_START + 500L, 2)), sent.lines);
    }

    @Test
    void aChangeOfSomebodyElseIsNotSent() {
        watching(EconomyFixtures.ALICE, COIN);

        deposit(EconomyFixtures.BOB, 500L, COIN, "tx1");
        service.tick();

        assertTrue(sent.lines.isEmpty(), "показ идёт только тому, чей счёт изменился");
    }

    @Test
    void theSameNumberIsNotSentTwice() {
        watching(EconomyFixtures.ALICE, COIN);

        deposit(EconomyFixtures.ALICE, 500L, COIN, "tx1");
        withdraw(EconomyFixtures.ALICE, 500L, "tx2");
        service.tick();

        assertTrue(sent.lines.isEmpty(), "за тик баланс вернулся к прежнему числу, слать нечего");
    }

    @Test
    void anotherCurrencyIsSkipped() {
        watching(EconomyFixtures.ALICE, COIN);

        deposit(EconomyFixtures.ALICE, 50L, CREDIT, "tx1");
        service.tick();

        assertTrue(sent.lines.isEmpty(), "игрок следит ровно за одной валютой");
    }

    @Test
    void anUnknownCurrencyFallsBackToTheDefault() {
        hud.watch(EconomyFixtures.ALICE, "doubloon");

        assertEquals(
            Collections.singletonList(line(EconomyFixtures.ALICE, COIN, COIN_START, 2)),
            sent.lines,
            "опечатка в клиентском файле не должна оставлять игрока без показа");
    }

    @Test
    void aNewRequestReplacesThePreviousCurrency() {
        watching(EconomyFixtures.ALICE, COIN);

        hud.watch(EconomyFixtures.ALICE, CREDIT);
        deposit(EconomyFixtures.ALICE, 500L, COIN, "tx1");
        service.tick();

        assertEquals(Collections.singletonList(line(EconomyFixtures.ALICE, CREDIT, CREDIT_START, 0)), sent.lines);
    }

    @Test
    void leavingForgetsTheMark() {
        watching(EconomyFixtures.ALICE, COIN);
        hud.forget(EconomyFixtures.ALICE);

        deposit(EconomyFixtures.ALICE, 500L, COIN, "tx1");
        service.tick();

        assertTrue(sent.lines.isEmpty(), "вышедшему игроку слать нечего");
    }

    @Test
    void aPlayerWhoCameBackGetsTheNumberAgain() {
        watching(EconomyFixtures.ALICE, COIN);
        hud.forget(EconomyFixtures.ALICE);

        hud.watch(EconomyFixtures.ALICE, COIN);

        assertEquals(
            Collections.singletonList(line(EconomyFixtures.ALICE, COIN, COIN_START, 2)),
            sent.lines,
            "после выхода прежняя отправленная сумма забыта, иначе показ остался бы пустым");
    }

    /** Игрок попросил показ, первый ответ уже посчитан и в записи не мешает. */
    private void watching(UUID player, String currencyId) {
        hud.watch(player, currencyId);
        sent.lines.clear();
    }

    private void deposit(UUID player, long amount, String currencyId, String transactionId) {
        service.execute(
            TransferRequest.deposit(player, amount, currencyId, transactionId, null, "test"),
            ChangeCause.COMMAND,
            false);
    }

    private void withdraw(UUID player, long amount, String transactionId) {
        service.execute(EconomyFixtures.withdraw(player, amount, transactionId), ChangeCause.COMMAND, false);
    }

    private static String line(UUID player, String currencyId, long amount, int decimals) {
        return player + " " + currencyId + " " + amount + " " + decimals;
    }

    /** Ники и мету отдаёт общая заготовка, ноду показа тест раздаёт сам. */
    private PlayerLookup lookup() {
        PlayerLookup names = EconomyFixtures.lookup();
        return new PlayerLookup() {

            @Override
            public String name(UUID player) {
                return names.name(player);
            }

            @Override
            public boolean has(UUID player, String node) {
                return EconomyNodes.BALANCE.equals(node) && withBalanceNode.contains(player);
            }

            @Override
            public Optional<String> meta(UUID player, String key) {
                return names.meta(player, key);
            }
        };
    }

    /** Ядро прав не поднялось: любой вопрос о праве кончается исключением. */
    private static PlayerLookup brokenLookup() {
        return new PlayerLookup() {

            @Override
            public String name(UUID player) {
                return null;
            }

            @Override
            public boolean has(UUID player, String node) {
                throw new IllegalStateException("PermissionService is not registered yet");
            }

            @Override
            public Optional<String> meta(UUID player, String key) {
                return Optional.empty();
            }
        };
    }

    /** Сток вместо сети: видно ровно то, что ушло бы игроку. */
    private static final class RecordingSink implements BalanceHudSink {

        private final List<String> lines = new ArrayList<>();

        @Override
        public void send(UUID player, String currencyId, long amount, int decimals) {
            lines.add(line(player, currencyId, amount, decimals));
        }
    }
}
