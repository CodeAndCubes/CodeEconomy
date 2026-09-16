package com.mrleonardos.codeeconomy.internal.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.command.ArgumentSpec;
import com.mrleonardos.codecore.api.command.ArgumentType;
import com.mrleonardos.codecore.api.command.CommandContext;
import com.mrleonardos.codecore.api.command.CommandMessages;
import com.mrleonardos.codecore.api.command.CommandNode;
import com.mrleonardos.codecore.api.command.CommandService;
import com.mrleonardos.codeeconomy.api.CurrencyIds;
import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.BalanceEntry;
import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.api.model.ResultCode;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;
import com.mrleonardos.codeeconomy.api.model.TransferRequest;
import com.mrleonardos.codeeconomy.api.model.TransferResult;
import com.mrleonardos.codeeconomy.internal.EconomyNodes;

class EconomyCommandsTest {

    private static final UUID SENDER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String CURRENCY = "currency";
    private static final String TX = "test-transaction";

    private FakeEconomy economy;
    private RecordingMutations mutations;
    private RecordingMaintenance maintenance;
    private TestSubjects subjects;
    private RecordingPresents presents;
    private EconomyCommands commands;

    @BeforeEach
    void setUp() {
        economy = new FakeEconomy(Collections.singletonList(CurrencyRecord.defaultCoin()));
        mutations = new RecordingMutations();
        maintenance = new RecordingMaintenance();
        subjects = new TestSubjects();
        presents = new RecordingPresents();
        commands = commands();
    }

    private EconomyCommands commands() {
        return new EconomyCommands(economy, mutations, maintenance, arguments(), subjects, presents, () -> 10);
    }

    @Test
    void rootsCarryNamesAliasesAndPermissions() {
        List<CommandNode> roots = commands.roots();

        assertEquals(Arrays.asList("balance", "pay", "baltop", "history", "eco"), names(roots));
        assertEquals(
            Arrays.asList("bal", "money"),
            roots.get(0)
                .aliases());
        assertEquals(
            EconomyNodes.BALANCE,
            roots.get(0)
                .permissionNode());
        assertEquals(
            EconomyNodes.PAY,
            roots.get(1)
                .permissionNode());
        assertEquals(
            EconomyNodes.BALTOP,
            roots.get(2)
                .permissionNode());
        assertEquals(
            EconomyNodes.HISTORY,
            roots.get(3)
                .permissionNode());
        assertNull(
            roots.get(4)
                .permissionNode(),
            "корень /eco виден всем, ветки прячут свои ноды");
    }

    @Test
    void adminBranchesCarryTheirOwnNodes() {
        CommandNode eco = eco();

        assertEquals(
            Arrays.asList(
                "give",
                "take",
                "set",
                "reset",
                "history",
                "freeze",
                "verify",
                "checkpoint",
                "compact",
                "unlock",
                "import"),
            names(eco.children()));
        assertEquals(EconomyNodes.ADMIN_UNLOCK, child(eco, "unlock").permissionNode());
        assertEquals(EconomyNodes.ADMIN_GIVE, child(eco, "give").permissionNode());
        assertEquals(EconomyNodes.ADMIN_TAKE, child(eco, "take").permissionNode());
        assertEquals(EconomyNodes.ADMIN_SET, child(eco, "set").permissionNode());
        assertEquals(EconomyNodes.ADMIN_RESET, child(eco, "reset").permissionNode());
        assertEquals(EconomyNodes.ADMIN_HISTORY, child(eco, "history").permissionNode());
        assertEquals(EconomyNodes.ADMIN_FREEZE, child(eco, "freeze").permissionNode());
        assertEquals(EconomyNodes.ADMIN_VERIFY, child(eco, "verify").permissionNode());
        assertEquals(EconomyNodes.ADMIN_CHECKPOINT, child(eco, "checkpoint").permissionNode());
        assertEquals(EconomyNodes.ADMIN_COMPACT, child(eco, "compact").permissionNode());
        assertEquals(EconomyNodes.ADMIN_IMPORT, child(eco, "import").permissionNode());
    }

    @Test
    void oneCurrencyLeavesNoCurrencyArgument() {
        for (CommandNode root : commands.roots()) {
            assertNull(argument(root, CURRENCY));
        }
        assertNull(argument(child(eco(), "give"), CURRENCY));
    }

    @Test
    void severalCurrenciesAddCurrencyArgument() {
        economy.currencies = Arrays.asList(
            CurrencyRecord.defaultCoin(),
            CurrencyRecord.builder("gem")
                .displayName("Gems")
                .decimals(0)
                .startBalance(10L)
                .maxBalance(1000L)
                .build());
        EconomyCommands several = commands();

        for (CommandNode root : several.roots()) {
            if (!root.name()
                .equals("balance")
                && !root.name()
                    .equals("pay")
                && !root.name()
                    .equals("baltop")) {
                assertNull(argument(root, CURRENCY), "у истории и корня /eco аргумента валюты нет");
                continue;
            }
            assertEquals(
                CurrencyIds.normalize("coin"),
                argument(root, CURRENCY).type()
                    .parse("COIN"));
        }
    }

    @Test
    void moderatorWithHistoryOnlySeesNoForbiddenBranch() {
        subjects.held.add(EconomyNodes.ADMIN_HISTORY);

        TestCommandContext context = new TestCommandContext();
        execute(eco(), context);

        assertEquals(1, presents.branches.size());
        assertEquals(
            "history",
            presents.branches.get(0)
                .get(0)
                .name());
    }

    @Test
    void strangerWithoutNodesReachesTheEmptyBranchList() {
        TestCommandContext context = new TestCommandContext();
        execute(eco(), context);

        assertEquals(1, presents.branches.size());
        assertTrue(
            presents.branches.get(0)
                .isEmpty(),
            "список веток фильтруется по правам и без нод пуст");
    }

    @Test
    void balanceShowsOwnBalance() {
        economy.balances.put(SENDER, 1250L);
        TestCommandContext context = new TestCommandContext();

        execute(root("balance"), context);

        assertEquals(1, presents.balances.size());
        RecordingPresents.Balance shown = presents.balances.get(0);
        assertEquals(SENDER, shown.player);
        assertTrue(shown.own);
        assertEquals("coin", shown.currency.id());
        assertEquals(1250L, shown.amount);
    }

    @Test
    void balanceOfAnotherPlayerNeedsTheNode() {
        economy.balances.put(TARGET, 100L);

        TestCommandContext refused = new TestCommandContext().set("player", TARGET.toString());
        execute(root("balance"), refused);

        assertTrue(refused.last().error);
        assertEquals(CommandMessages.NO_PERMISSION, refused.last().key);
        assertTrue(mutations.requests.isEmpty());
        assertTrue(presents.balances.isEmpty());

        subjects.held.add(EconomyNodes.BALANCE_OTHER);
        subjects.names.put(TARGET, "Alex");
        TestCommandContext allowed = new TestCommandContext().set("player", TARGET.toString());
        execute(root("balance"), allowed);

        assertEquals(1, presents.balances.size());
        RecordingPresents.Balance shown = presents.balances.get(0);
        assertEquals(TARGET, shown.player);
        assertFalse(shown.own);
        assertEquals(100L, shown.amount);
    }

    @Test
    void balanceFromConsoleWithoutTargetAsksForAPlayer() {
        subjects.self = Optional.empty();
        TestCommandContext context = new TestCommandContext();

        execute(root("balance"), context);

        assertTrue(context.last().error);
        assertEquals(CommandMessages.PLAYERS_ONLY, context.last().key);
    }

    @Test
    void payGoesThroughMutationsWithCommandCause() {
        economy.balances.put(SENDER, 10000L);
        subjects.names.put(TARGET, "Alex");
        mutations.result = TransferResult.success(TX, 8950L, 1050L);

        TestCommandContext context = new TestCommandContext().set("player", TARGET.toString())
            .set("amount", "10.50");
        execute(root("pay"), context);

        assertEquals(1, mutations.requests.size());
        TransferRequest request = mutations.requests.get(0).request;
        assertEquals(Optional.of(SENDER), request.from());
        assertEquals(Optional.of(TARGET), request.to());
        assertEquals(1050L, request.amount());
        assertEquals("coin", request.currencyId());
        assertEquals(ChangeCause.COMMAND, mutations.requests.get(0).cause);
        assertTrue(
            request.transactionId()
                .startsWith("cmd:"),
            "идентификатор команды начинается с cmd: " + request.transactionId());
        assertEquals(EconomyMessages.PAY_SENT, context.last().key);
        assertEquals("Alex", context.last().arguments.get(1));
        assertEquals("10.50 $", context.last().arguments.get(2));
    }

    @Test
    void insufficientAnswerCarriesBothAmounts() {
        economy.balances.put(SENDER, 100L);
        mutations.result = TransferResult.failure(ResultCode.INSUFFICIENT, TX);

        TestCommandContext context = new TestCommandContext().set("player", TARGET.toString())
            .set("amount", "5");
        execute(root("pay"), context);

        assertTrue(context.last().error);
        assertEquals(EconomyMessages.FAILURE_INSUFFICIENT, context.last().key);
        assertEquals("1.00 $", context.last().arguments.get(0));
        assertEquals("5.00 $", context.last().arguments.get(1));
    }

    @Test
    void duplicateCountsAsApplied() {
        mutations.result = TransferResult.duplicate(TX, 100L, 900L);

        TestCommandContext context = new TestCommandContext().set("player", TARGET.toString())
            .set("amount", "1");
        execute(root("pay"), context);

        assertEquals(EconomyMessages.PAY_SENT, context.last().key);
    }

    @Test
    void sameAccountAnswerComesFromTheEngine() {
        mutations.result = TransferResult.failure(ResultCode.SAME_ACCOUNT, TX);

        TestCommandContext context = new TestCommandContext().set("player", SENDER.toString())
            .set("amount", "1");
        execute(root("pay"), context);

        assertTrue(context.last().error);
        assertEquals(EconomyMessages.FAILURE_SAME_ACCOUNT, context.last().key);
    }

    @Test
    void extraFractionDigitIsCaughtBeforeTheEngine() {
        TestCommandContext context = new TestCommandContext().set("player", TARGET.toString())
            .set("amount", "1.234");
        execute(root("pay"), context);

        assertTrue(context.last().error);
        assertEquals(EconomyMessages.FAILURE_BAD_AMOUNT, context.last().key);
        assertTrue(mutations.requests.isEmpty(), "плохая сумма до движка не доходит");
    }

    @Test
    void oversizedAmountTokenIsCaught() {
        StringBuilder token = new StringBuilder("1");
        for (int index = 0; index < AmountArgument.MAX_INPUT; index++) {
            token.append('0');
        }
        TestCommandContext context = new TestCommandContext().set("player", TARGET.toString())
            .set("amount", token.toString());
        execute(root("pay"), context);

        assertTrue(context.last().error);
        assertEquals(EconomyMessages.FAILURE_BAD_AMOUNT, context.last().key);
        assertTrue(mutations.requests.isEmpty());
    }

    @Test
    void unknownCurrencyIsAnswered() {
        TestCommandContext context = new TestCommandContext().set("player", TARGET.toString())
            .set("amount", "1")
            .set("currency", "gems");
        execute(root("pay"), context);

        assertTrue(context.last().error);
        assertEquals(EconomyMessages.FAILURE_UNKNOWN_CURRENCY, context.last().key);
        assertEquals("gems", context.last().arguments.get(0));
        assertTrue(mutations.requests.isEmpty());
    }

    @Test
    void takeBelowFloorAnswersBelowFloor() {
        mutations.result = TransferResult.failure(ResultCode.BELOW_FLOOR, TX, 0L, null);

        TestCommandContext context = new TestCommandContext().set("player", TARGET.toString())
            .set("amount", "1");
        execute(child(eco(), "take"), context);

        assertTrue(context.last().error);
        assertEquals(EconomyMessages.FAILURE_BELOW_FLOOR, context.last().key);
        assertEquals("0.00 $", context.last().arguments.get(0));
    }

    @Test
    void setAcceptsNegativeAmountAndResetCarriesNoAmount() {
        mutations.result = TransferResult.success(TX, null, -500L);

        TestCommandContext set = new TestCommandContext().set("player", TARGET.toString())
            .set("amount", "-5");
        execute(child(eco(), "set"), set);

        assertEquals(EconomyMessages.SET_DONE, set.last().key);
        assertEquals(-500L, mutations.requests.get(0).request.amount());

        mutations.result = TransferResult.success(TX, null, 25000L);
        TestCommandContext reset = new TestCommandContext().set("player", TARGET.toString());
        execute(child(eco(), "reset"), reset);

        assertEquals(EconomyMessages.RESET_DONE, reset.last().key);
        assertEquals(0L, mutations.requests.get(1).request.amount());
    }

    @Test
    void consoleGiveCarriesNoActor() {
        subjects.self = Optional.empty();
        mutations.result = TransferResult.success(TX, null, 1000L);

        TestCommandContext context = new TestCommandContext().set("player", TARGET.toString())
            .set("amount", "10");
        execute(child(eco(), "give"), context);

        assertEquals(EconomyMessages.GIVE_DONE, context.last().key);
        assertFalse(
            mutations.requests.get(0).request.actor()
                .isPresent(),
            "консоль не оставляет автора");
    }

    @Test
    void freezeReachesTheMaintenanceInBothDirections() {
        maintenance.outcome = MaintenanceOutcome.success(1L);
        subjects.names.put(TARGET, "Alex");

        TestCommandContext on = new TestCommandContext().set("player", TARGET.toString())
            .set("state", "on");
        execute(child(eco(), "freeze"), on);

        assertEquals(Boolean.TRUE, maintenance.frozen.get(0));
        assertEquals(EconomyMessages.FREEZE_ON, on.last().key);

        TestCommandContext off = new TestCommandContext().set("player", TARGET.toString())
            .set("state", "off");
        execute(child(eco(), "freeze"), off);

        assertEquals(Boolean.FALSE, maintenance.frozen.get(1));
        assertEquals(EconomyMessages.FREEZE_OFF, off.last().key);
    }

    @Test
    void frozenAccountAnswersWithTheTargetName() {
        maintenance.outcome = MaintenanceOutcome.failure(ResultCode.ACCOUNT_FROZEN);
        subjects.names.put(TARGET, "Alex");

        TestCommandContext context = new TestCommandContext().set("player", TARGET.toString())
            .set("state", "on");
        execute(child(eco(), "freeze"), context);

        assertTrue(context.last().error);
        assertEquals(EconomyMessages.FAILURE_FROZEN, context.last().key);
        assertEquals("Alex", context.last().arguments.get(0));
    }

    @Test
    void verifyStartsTheBackgroundCheck() {
        maintenance.outcome = MaintenanceOutcome.success(0L);

        TestCommandContext context = new TestCommandContext();
        execute(child(eco(), "verify"), context);

        assertEquals(1, maintenance.calls);
        assertEquals(EconomyMessages.VERIFY_STARTED, context.last().key);
        assertFalse(context.last().error);
    }

    @Test
    void importFlagsReachTheMaintenance() {
        maintenance.outcome = MaintenanceOutcome.success(0L, Long.valueOf(2L), Long.valueOf(1L));

        TestCommandContext dry = new TestCommandContext().set("format", "flatjson")
            .set("flags", "");
        execute(child(eco(), "import"), dry);

        assertEquals("flatjson", maintenance.format);
        assertEquals(1, presents.reports.size());
        RecordingPresents.Report report = presents.reports.get(0);
        assertFalse(report.apply);
        assertEquals(2L, report.moved);
        assertEquals(1L, report.rejected);

        TestCommandContext apply = new TestCommandContext().set("format", "essentials")
            .set("flags", "--apply");
        execute(child(eco(), "import"), apply);

        assertEquals(Boolean.TRUE, maintenance.apply);
        assertEquals(2, presents.reports.size());
        assertTrue(presents.reports.get(1).apply, "прогон с --apply отмечается в отчёте");

        TestCommandContext broken = new TestCommandContext().set("format", "flatjson")
            .set("flags", "--bogus");
        execute(child(eco(), "import"), broken);

        assertTrue(broken.last().error);
        assertEquals(EconomyMessages.FAILURE_INVALID_REQUEST, broken.last().key);
        assertTrue(maintenance.calls == 2, "чужой флаг до обслуживания не доходит");
    }

    @Test
    void baltopHandsTheWholeTopToThePresent() {
        economy.top.add(BalanceEntry.of(SENDER, "Steve", 2000L));
        economy.top.add(BalanceEntry.of(TARGET, null, 1000L));

        TestCommandContext context = new TestCommandContext();
        execute(root("baltop"), context);

        assertEquals(1, presents.tops.size());
        RecordingPresents.Top shown = presents.tops.get(0);
        assertEquals("coin", shown.currency.id());
        assertEquals(2, shown.entries.size());
        assertFalse(
            shown.entries.get(1)
                .name()
                .isPresent(),
            "запись без имени доходит как есть, подпись выбирает показ");
        assertEquals(1, shown.page);

        economy.top.clear();
        TestCommandContext empty = new TestCommandContext();
        execute(root("baltop"), empty);

        assertEquals(2, presents.tops.size());
        assertTrue(presents.tops.get(1).entries.isEmpty(), "пустой топ доходит пустым списком");
    }

    @Test
    void historyHandsRecordsWithReasonToThePresent() {
        economy.history.add(
            TransactionRecord.builder(TransactionRecord.Kind.TRANSFER, "coin", "cmd:one")
                .seq(1L)
                .ts(1L)
                .from(SENDER, 900L)
                .to(TARGET, 1100L)
                .cause(ChangeCause.COMMAND)
                .build());
        economy.history.add(
            TransactionRecord.builder(TransactionRecord.Kind.DEPOSIT, "coin", "shop:42")
                .seq(2L)
                .ts(2L)
                .to(SENDER, 1400L)
                .cause(ChangeCause.API)
                .reason("shop:42")
                .build());

        TestCommandContext context = new TestCommandContext();
        execute(root("history"), context);

        assertEquals(1, presents.histories.size());
        RecordingPresents.History shown = presents.histories.get(0);
        assertEquals(SENDER, shown.player);
        assertTrue(shown.own);
        assertEquals(2, shown.records.size());
        assertEquals(
            "shop:42",
            shown.records.get(1)
                .reason()
                .get());
        assertEquals(1, shown.page);
    }

    @Test
    void historyWithoutRecordsAnswersEmpty() {
        TestCommandContext context = new TestCommandContext();
        execute(root("history"), context);

        assertEquals(1, presents.histories.size());
        assertTrue(presents.histories.get(0).records.isEmpty());
    }

    @Test
    void registerHandsAllRootsToTheCore() {
        RecordingCommandService service = new RecordingCommandService();

        commands.register(service);

        assertEquals(5, service.registered.size());
    }

    private CommandNode eco() {
        for (CommandNode root : commands.roots()) {
            if (root.name()
                .equals("eco")) {
                return root;
            }
        }
        throw new AssertionError("no /eco root");
    }

    private CommandNode root(String name) {
        for (CommandNode node : commands.roots()) {
            if (node.name()
                .equals(name)) {
                return node;
            }
        }
        throw new AssertionError("No root command " + name);
    }

    private static CommandNode child(CommandNode node, String name) {
        for (CommandNode child : node.children()) {
            if (child.name()
                .equals(name)) {
                return child;
            }
        }
        throw new AssertionError("No child " + name + " under " + node.name());
    }

    private static ArgumentSpec argument(CommandNode node, String name) {
        for (ArgumentSpec spec : node.arguments()) {
            if (spec.name()
                .equals(name)) {
                return spec;
            }
        }
        return null;
    }

    private static List<String> names(List<CommandNode> nodes) {
        List<String> found = new ArrayList<>();
        for (CommandNode node : nodes) {
            found.add(node.name());
        }
        return found;
    }

    private static void execute(CommandNode node, TestCommandContext context) {
        for (ArgumentSpec spec : node.arguments()) {
            if (!context.has(spec.name())) {
                continue;
            }
            Object raw = context.get(spec.name());
            context.put(
                spec.name(),
                spec.type()
                    .parse(String.valueOf(raw)));
        }
        node.action()
            .run(context);
    }

    private static EconomyArguments arguments() {
        return new EconomyArguments() {

            @Override
            public ArgumentType<UUID> player() {
                return UUID::fromString;
            }

            @Override
            public ArgumentType<String> currency() {
                return CurrencyIds::normalize;
            }
        };
    }

    private static final class FakeEconomy implements com.mrleonardos.codeeconomy.api.EconomyService {

        private List<CurrencyRecord> currencies;
        private final Map<UUID, Long> balances = new LinkedHashMap<>();
        private final List<BalanceEntry> top = new ArrayList<>();
        private final List<TransactionRecord> history = new ArrayList<>();

        FakeEconomy(List<CurrencyRecord> currencies) {
            this.currencies = currencies;
        }

        @Override
        public List<CurrencyRecord> currencies() {
            return currencies;
        }

        @Override
        public Optional<CurrencyRecord> currency(String currencyId) {
            for (CurrencyRecord currency : currencies) {
                if (currency.id()
                    .equals(currencyId)) {
                    return Optional.of(currency);
                }
            }
            return Optional.empty();
        }

        @Override
        public String defaultCurrencyId() {
            return currencies.get(0)
                .id();
        }

        @Override
        public long balance(UUID player, String currencyId) {
            Long amount = balances.get(player);
            return amount == null ? currencies.get(0)
                .startBalance() : amount;
        }

        @Override
        public boolean has(UUID player, long amount, String currencyId) {
            return balance(player, currencyId) >= amount;
        }

        @Override
        public Optional<AccountView> account(UUID player) {
            return Optional.empty();
        }

        @Override
        public TransferResult transfer(TransferRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransferResult deposit(TransferRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransferResult withdraw(TransferRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransferResult set(TransferRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransferResult reset(TransferRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<TransferResult> submit(TransferRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TransactionRecord> history(UUID player, int page, int pageSize) {
            return history;
        }

        @Override
        public List<BalanceEntry> top(String currencyId, int page, int pageSize) {
            return top;
        }
    }

    private static final class RecordingMutations implements EconomyMutations {

        private final List<Applied> requests = new ArrayList<>();
        private TransferResult result = TransferResult.success("test", null, null);

        @Override
        public TransferResult apply(TransferRequest request, ChangeCause cause, boolean floorBypass) {
            requests.add(new Applied(request, cause, floorBypass));
            return result;
        }

        private static final class Applied {

            private final TransferRequest request;
            private final ChangeCause cause;
            private final boolean floorBypass;

            private Applied(TransferRequest request, ChangeCause cause, boolean floorBypass) {
                this.request = request;
                this.cause = cause;
                this.floorBypass = floorBypass;
            }
        }
    }

    private static final class RecordingMaintenance implements EconomyMaintenance {

        private MaintenanceOutcome outcome = MaintenanceOutcome.success(0L);
        private final List<Boolean> frozen = new ArrayList<>();
        private String format;
        private Boolean apply;
        private int calls;

        @Override
        public MaintenanceOutcome freeze(UUID player, boolean frozen, UUID actor, String transactionId) {
            this.frozen.add(frozen);
            calls++;
            return outcome;
        }

        @Override
        public MaintenanceOutcome verify() {
            calls++;
            return outcome;
        }

        @Override
        public MaintenanceOutcome checkpoint() {
            calls++;
            return outcome;
        }

        @Override
        public MaintenanceOutcome compact() {
            calls++;
            return outcome;
        }

        @Override
        public MaintenanceOutcome unlock() {
            calls++;
            return outcome;
        }

        @Override
        public MaintenanceOutcome importBalances(String format, String file, boolean apply) {
            this.format = format;
            this.apply = apply;
            calls++;
            return outcome;
        }
    }

    private static final class TestSubjects implements EconomySubjects {

        private Optional<UUID> self = Optional.of(SENDER);
        private final Set<String> held = new LinkedHashSet<>();
        private final Map<UUID, String> names = new LinkedHashMap<>();

        @Override
        public Optional<UUID> subjectOf(CommandContext context) {
            return self;
        }

        @Override
        public Optional<String> playerName(UUID player) {
            return Optional.ofNullable(names.get(player));
        }

        @Override
        public boolean senderHas(CommandContext context, String node) {
            return held.contains(node);
        }
    }

    /** Показ, который помнит вызовы: команды обязаны дойти до него с уже готовыми данными. */
    private static final class RecordingPresents implements EconomyPresents {

        private final List<List<CommandNode>> branches = new ArrayList<>();
        private final List<Balance> balances = new ArrayList<>();
        private final List<Top> tops = new ArrayList<>();
        private final List<History> histories = new ArrayList<>();
        private final List<Report> reports = new ArrayList<>();

        @Override
        public void branches(CommandContext context, List<CommandNode> visible) {
            branches.add(new ArrayList<>(visible));
        }

        @Override
        public void balance(CommandContext context, UUID player, boolean own, CurrencyRecord currency, long amount) {
            balances.add(new Balance(player, own, currency, amount));
        }

        @Override
        public void top(CommandContext context, CurrencyRecord currency, List<BalanceEntry> entries, int page) {
            tops.add(new Top(currency, new ArrayList<>(entries), page));
        }

        @Override
        public void history(CommandContext context, UUID player, boolean own, List<TransactionRecord> records,
            int page) {
            histories.add(new History(player, own, new ArrayList<>(records), page));
        }

        @Override
        public void importReport(CommandContext context, boolean apply, long moved, long rejected,
            List<MaintenanceOutcome.Row> rows) {
            reports.add(new Report(apply, moved, rejected));
        }

        static final class Balance {

            final UUID player;
            final boolean own;
            final CurrencyRecord currency;
            final long amount;

            Balance(UUID player, boolean own, CurrencyRecord currency, long amount) {
                this.player = player;
                this.own = own;
                this.currency = currency;
                this.amount = amount;
            }
        }

        static final class Top {

            final CurrencyRecord currency;
            final List<BalanceEntry> entries;
            final int page;

            Top(CurrencyRecord currency, List<BalanceEntry> entries, int page) {
                this.currency = currency;
                this.entries = entries;
                this.page = page;
            }
        }

        static final class History {

            final UUID player;
            final boolean own;
            final List<TransactionRecord> records;
            final int page;

            History(UUID player, boolean own, List<TransactionRecord> records, int page) {
                this.player = player;
                this.own = own;
                this.records = records;
                this.page = page;
            }
        }

        static final class Report {

            final boolean apply;
            final long moved;
            final long rejected;

            Report(boolean apply, long moved, long rejected) {
                this.apply = apply;
                this.moved = moved;
                this.rejected = rejected;
            }
        }
    }

    private static final class RecordingCommandService implements CommandService {

        private final List<CommandNode> registered = new ArrayList<>();

        @Override
        public void register(CommandNode root) {
            registered.add(root);
        }
    }
}
