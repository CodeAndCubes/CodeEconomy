package com.mrleonardos.codeeconomy.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.minecraft.command.ICommandSender;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.command.ArgumentTypes;
import com.mrleonardos.codecore.api.command.CommandContext;
import com.mrleonardos.codecore.api.command.CommandNode;
import com.mrleonardos.codecore.api.command.CommandSender;
import com.mrleonardos.codecore.api.config.AuditSettings;
import com.mrleonardos.codecore.api.config.StorageSettings;
import com.mrleonardos.codecore.platform.Senders;
import com.mrleonardos.codecore.platform.present.Pages;
import com.mrleonardos.codeeconomy.api.EconomyService;
import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.BalanceEntry;
import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;
import com.mrleonardos.codeeconomy.api.model.TransferRequest;
import com.mrleonardos.codeeconomy.api.model.TransferResult;
import com.mrleonardos.codeeconomy.internal.EconomyConfig;
import com.mrleonardos.codeeconomy.internal.EconomyNodes;
import com.mrleonardos.codeeconomy.internal.EconomySection;
import com.mrleonardos.codeeconomy.internal.EconomySettings;
import com.mrleonardos.codeeconomy.internal.command.EconomySubjects;
import com.mrleonardos.codeeconomy.internal.command.MaintenanceOutcome;

/**
 * Карточки и страницы собираются из полей экономики, без запуска игры.
 *
 * <p>
 * Проверяется сборка: цвета, клики и подсказки стоят на своих местах, права отрезают кнопки, которых
 * отправитель не заслужил. Отправка идёт через консоль: плоская половина показа и видна, и проверяема.
 * Ожидания на английском: это подложка линейки, лежащая под любым языком.
 */
class EconomyPresentTest {

    private static final UUID SENDER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final TestEconomy economy = new TestEconomy();
    private final TestSubjects subjects = new TestSubjects();
    private final EconomySection section = new EconomySection();

    EconomyPresentTest() {
        subjects.names.put(SENDER, "Steve");
        subjects.names.put(TARGET, "Alex");
    }

    @Test
    @DisplayName("карточка веток ведёт в каждую, пустой список говорит об этом")
    void theBranchesCardLeadsToEveryBranch() {
        CommandNode verify = CommandNode.literal("verify")
            .usage("codeeconomy.command.usage.eco.verify");
        CommandNode give = CommandNode.literal("give")
            .arg("player", ArgumentTypes.word())
            .usage("codeeconomy.command.usage.eco.give");

        List<IChatComponent> lines = present().branchesCard(Arrays.asList(verify, give))
            .build();

        assertEquals("Branches of /eco", plain(lines.get(0)));
        assertEquals("verify · /eco verify", plain(lines.get(1)), "ветка без аргументов исполняется кликом");
        assertTrue(hasClick(lines.get(1), "/eco verify"));
        assertEquals("give · /eco give <player> <amount> [currency]", plain(lines.get(2)));
        assertTrue(hasClick(lines.get(2), "/eco give "), "ветка с обязательным аргументом подставляет команду");

        List<IChatComponent> empty = present().branchesCard(new ArrayList<CommandNode>())
            .build();
        assertEquals("Branches of /eco", plain(empty.get(0)));
        assertEquals("No branches available", plain(empty.get(1)));
    }

    @Test
    @DisplayName("карточка своего баланса несёт лимиты перевода и кнопки по правам")
    void theOwnBalanceCardCarriesLimitsAndButtons() {
        subjects.held.add(EconomyNodes.PAY);
        subjects.held.add(EconomyNodes.HISTORY);
        subjects.held.add(EconomyNodes.BALTOP);
        section.minTransfer = 100L;
        section.maxTransfer = 250000L;

        List<IChatComponent> lines = present().balanceCard(context(), SENDER, true, economy.coin(), 1250L)
            .build();

        assertEquals("Steve", plain(lines.get(0)));
        assertEquals(EnumChatFormatting.YELLOW, color(lines.get(0), 0), "заголовок несёт акцент мода");
        assertEquals("Currency: Coins", plain(lines.get(1)));
        assertEquals("Balance: 12.50 $", plain(lines.get(2)));
        assertEquals("Transfer: from 1.00 $ to 2500.00 $", plain(lines.get(3)));
        assertEquals("[History]   [Top]   [Send]", plain(lines.get(4)));
        assertTrue(hasClick(lines.get(4), "/history"), "кнопка истории исполняет свою команду");
        assertTrue(hasClick(lines.get(4), "/baltop"), "кнопка топа открывает первую страницу");
        assertTrue(hasClick(lines.get(4), "/pay "), "кнопка перевода подставляет начало команды");
    }

    @Test
    @DisplayName("без прав карточка своего баланса лишается и лимитов, и кнопок")
    void theOwnBalanceCardFollowsTheRights() {
        List<IChatComponent> lines = present().balanceCard(context(), SENDER, true, economy.coin(), 1250L)
            .build();

        assertEquals(3, lines.size(), "валюта и баланс остались, лимитов и футера нет");
        for (IChatComponent line : lines) {
            assertFalse(plain(line).contains("["), "кнопок без нод не бывает");
        }
    }

    @Test
    @DisplayName("карточка чужого баланса предлагает операции с нодой администратора")
    void theForeignBalanceCardOffersTheAdminHistory() {
        assertEquals(
            3,
            present().balanceCard(context(), TARGET, false, economy.coin(), 100L)
                .build()
                .size(),
            "без ноды операций нет, карточка короче");

        subjects.held.add(EconomyNodes.ADMIN_HISTORY);
        List<IChatComponent> lines = present().balanceCard(context(), TARGET, false, economy.coin(), 100L)
            .build();

        assertEquals("Alex", plain(lines.get(0)));
        assertEquals("[Operations]", plain(lines.get(3)));
        assertTrue(hasClick(lines.get(3), "/eco history Alex"));
    }

    @Test
    @DisplayName("топ строит строки только среза, место продолжает нумерацию, консоли строка положения")
    void theTopBuildsOnlyTheSliceAndNumbersPlaces() {
        subjects.held.add(EconomyNodes.BALANCE_OTHER);
        List<BalanceEntry> entries = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            entries.add(BalanceEntry.of(SENDER, "Steve", 5000L - index));
        }

        Console console = new Console();
        present().top(over(console), economy.coin(), entries, 2);

        List<IChatComponent> lines = console.heard;
        assertEquals("Top for Coins · total 5", plain(lines.get(0)));
        assertEquals("4. Steve · 49.97 $", plain(lines.get(1)), "нумерация идёт сквозь страницы");
        assertEquals("5. Steve · 49.96 $", plain(lines.get(2)));
        assertEquals("Page 2 of 2", plain(lines.get(3)), "консоли вместо кнопок достаётся строка положения");

        IChatComponent row = present().topRows(context(), economy.coin(), entries.subList(3, 5), 3)
            .get(0)
            .build();
        assertTrue(hasClick(row, "/balance Steve"), "строка ведёт на баланс игрока");
        assertTrue(hover(row).startsWith("Balance: "), "подсказка строки называет сумму");
    }

    @Test
    @DisplayName("футер листания это кнопки: назад и далее с положением в подсказке")
    void theTurnsCarryButtonsWithPositions() {
        IChatComponent footer = present().turns(2, 3, present().topTurn(economy.coin()))
            .build();

        assertEquals("[Back]   2/3   [Next]", plain(footer));
        assertTrue(hasClick(footer, "/baltop 1"), "назад ведёт на прошлую страницу");
        assertTrue(hasClick(footer, "/baltop 3"), "далее ведёт на следующую");
        assertTrue(
            hovers(footer).contains("Page 1 of 3") && hovers(footer).contains("Page 3 of 3"),
            "подсказки называют страницы по кликам");

        IChatComponent last = present().turns(3, 3, "/history %d")
            .build();
        assertFalse(hasClick(last, "/history 4"), "последняя страница без далее");
    }

    @Test
    @DisplayName("без ноды смотреть чужой баланс строка топа не кликается, одной странице листание не нужно")
    void theTopRowStaysPlainWithoutTheNode() {
        List<BalanceEntry> entries = Arrays.asList(BalanceEntry.of(SENDER, "Steve", 5000L));

        IChatComponent row = present().topRows(context(), economy.coin(), entries, 0)
            .get(0)
            .build();

        assertEquals("1. Steve · 50.00 $", plain(row));
        assertFalse(hasClick(row, "/balance Steve"), "клика без ноды нет");

        Console console = new Console();
        present().top(over(console), economy.coin(), entries, 1);
        assertEquals(3, console.heard.size(), "шапка, строка и пояснение, листания одной странице не нужно");
    }

    @Test
    @DisplayName("счёт без имени показывает идентификатор и не кликается даже с нодой")
    void theUnnamedAccountRowCarriesNoClick() {
        subjects.held.add(EconomyNodes.BALANCE_OTHER);
        UUID stranger = UUID.fromString("00000000-0000-0000-0000-000000000003");

        IChatComponent row = present()
            .topRows(context(), economy.coin(), Arrays.asList(BalanceEntry.of(stranger, null, 1000L)), 0)
            .get(0)
            .build();

        assertEquals("1. " + stranger + " · 10.00 $", plain(row));
        assertFalse(hasClick(row, "/balance " + stranger), "аргумент игрока идентификатор не разбирает, клика нет");
    }

    @Test
    @DisplayName("чужая история листается командой с именем игрока")
    void theForeignHistoryTurnsByItsOwnCommand() {
        assertEquals("/history %d", present().historyTurn(SENDER, true));
        assertEquals("/eco history Alex %d", present().historyTurn(TARGET, false));
    }

    @Test
    @DisplayName("валюте не по умолчанию листание и строки топа несут явный аргумент")
    void theSecondaryCurrencyCarriesItsArgument() {
        subjects.held.add(EconomyNodes.BALANCE_OTHER);
        CurrencyRecord gem = CurrencyRecord.builder("gem")
            .displayName("Gems")
            .decimals(0)
            .startBalance(10L)
            .maxBalance(1000L)
            .build();
        economy.currencies.add(gem);

        assertEquals("/baltop %d gem", present().topTurn(gem));
        IChatComponent row = present().topRows(context(), gem, Arrays.asList(BalanceEntry.of(TARGET, "Alex", 500L)), 0)
            .get(0)
            .build();
        assertEquals("1. Alex · 500 gem", plain(row));
        assertTrue(hasClick(row, "/balance Alex gem"));
    }

    @Test
    @DisplayName("строка истории красит расход красным, приход зелёным, идентификатор в подсказке")
    void historyRowsColorTheDirection() {
        List<TransactionRecord> records = Arrays.asList(paidToFriend(), receivedFromShop(), tookFromAccount());

        List<IChatComponent> lines = Pages.of(present().historyRows(SENDER, records), 3)
            .card(1, "Operations of Steve", "/history %d")
            .build();

        assertEquals("Transfer (command): 9.00 $", plain(lines.get(1)));
        assertEquals(EnumChatFormatting.RED, color(lines.get(1), 4), "перевод со счёта это расход");
        assertTrue(hover(lines.get(1)).endsWith("cmd:one"), "подсказка называет идентификатор операции");
        assertEquals("Deposit (foreign mod): 14.00 $ · reason: shop:42", plain(lines.get(2)));
        assertEquals(EnumChatFormatting.GREEN, color(lines.get(2), 4), "поступление на счёт это приход");
        assertEquals("Withdraw (command): 8.00 $", plain(lines.get(3)));
    }

    @Test
    @DisplayName("отчёт импорта это карточка со сводкой и построчными пояснениями")
    void theImportReportCarriesRows() {
        List<MaintenanceOutcome.Row> rows = Arrays.asList(
            MaintenanceOutcome.Row.of("codeeconomy.message.import.row", "e34d", "25.00 $"),
            MaintenanceOutcome.Row.of("codeeconomy.message.import.more", Integer.valueOf(4)));

        Console console = new Console();
        present().importReport(over(console), false, 2L, 1L, rows);

        List<IChatComponent> lines = console.heard;
        assertEquals("Balance import", plain(lines.get(0)));
        assertEquals("Ready to import 2, would reject 1", plain(lines.get(1)));
        assertEquals("e34d gets 25.00 $", plain(lines.get(2)));
        assertEquals("More rows: 4", plain(lines.get(3)));
    }

    @Test
    @DisplayName("консоль получает топ и пустую историю плоским текстом без стилей")
    void theConsoleReceivesFlatPages() {
        Console console = new Console();
        present().top(over(console), economy.coin(), Arrays.asList(BalanceEntry.of(SENDER, "Steve", 5000L)), 1);

        assertEquals(3, console.heard.size());
        assertEquals("Top for Coins · total 1", plain(console.heard.get(0)));
        assertEquals("1. Steve · 50.00 $", plain(console.heard.get(1)));
        assertEquals(
            "An account with no operations stands in the top with the starting balance",
            plain(console.heard.get(2)),
            "пояснение о стартовых балансах остаётся в выводе");

        Console empty = new Console();
        present().history(over(empty), TARGET, false, new ArrayList<TransactionRecord>(), 1);
        assertEquals("Operations of Alex · total 0", plain(empty.heard.get(0)));
        assertEquals("No operations so far", plain(empty.heard.get(1)));

        List<TransactionRecord> records = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            records.add(paidToFriend());
        }
        Console pages = new Console();
        present().history(over(pages), SENDER, true, records, 2);
        assertEquals("Operations of Steve · total 4", plain(pages.heard.get(0)));
        assertEquals("Transfer (command): 9.00 $", plain(pages.heard.get(1)));
        assertEquals("Page 2 of 2", plain(pages.heard.get(2)));

        for (IChatComponent message : console.heard) {
            assertNull(
                message.getChatStyle()
                    .getColor(),
                "у консоли не должно быть цвета");
            assertNull(
                message.getChatStyle()
                    .getChatClickEvent(),
                "у консоли не должно быть кликов");
        }
    }

    private EconomyPresent present() {
        EconomyConfig config = EconomyConfig
            .of(EconomySettings.defaults(), section, new StorageSettings("json", 30), new AuditSettings(false, false));
        return new EconomyPresent(economy, config, subjects, () -> 3);
    }

    /** Контекст без отправки: строители карточек спрашивают через него права. */
    private static CommandContext context() {
        return over(new Console());
    }

    private static TransactionRecord paidToFriend() {
        return TransactionRecord.builder(TransactionRecord.Kind.TRANSFER, "coin", "cmd:one")
            .seq(1L)
            .ts(1L)
            .from(SENDER, 900L)
            .to(TARGET, 1100L)
            .cause(ChangeCause.COMMAND)
            .build();
    }

    private static TransactionRecord receivedFromShop() {
        return TransactionRecord.builder(TransactionRecord.Kind.DEPOSIT, "coin", "shop:42")
            .seq(2L)
            .ts(2L)
            .to(SENDER, 1400L)
            .cause(ChangeCause.API)
            .reason("shop:42")
            .build();
    }

    private static TransactionRecord tookFromAccount() {
        return TransactionRecord.builder(TransactionRecord.Kind.WITHDRAW, "coin", "cmd:two")
            .seq(3L)
            .ts(3L)
            .from(SENDER, 800L)
            .cause(ChangeCause.COMMAND)
            .build();
    }

    /** Контекст команды над консолью: показ уходит прямо в принятые консолью строки. */
    private static CommandContext over(final Console console) {
        return new CommandContext() {

            private final CommandSender caller = Senders.of(console);

            @Override
            public CommandSender caller() {
                return caller;
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T get(String name) {
                return (T) null;
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> T getOrDefault(String name, T fallback) {
                return fallback;
            }

            @Override
            public boolean has(String name) {
                return false;
            }

            @Override
            public void reply(String translationKey, Object... arguments) {}

            @Override
            public void replyError(String translationKey, Object... arguments) {}
        };
    }

    private static String plain(IChatComponent line) {
        return line.getUnformattedText();
    }

    private static EnumChatFormatting color(IChatComponent line, int piece) {
        return line.getSiblings()
            .get(piece)
            .getChatStyle()
            .getColor();
    }

    private static String hover(IChatComponent line) {
        for (IChatComponent piece : line.getSiblings()) {
            if (piece.getChatStyle()
                .getChatHoverEvent() != null) {
                return piece.getChatStyle()
                    .getChatHoverEvent()
                    .getValue()
                    .getUnformattedText();
            }
        }
        return "";
    }

    /** Все подсказки строки: у футера листания их две, по кнопке на страницу. */
    private static List<String> hovers(IChatComponent line) {
        List<String> found = new ArrayList<>();
        for (IChatComponent piece : line.getSiblings()) {
            if (piece.getChatStyle()
                .getChatHoverEvent() != null) {
                found.add(
                    piece.getChatStyle()
                        .getChatHoverEvent()
                        .getValue()
                        .getUnformattedText());
            }
        }
        return found;
    }

    private static boolean hasClick(IChatComponent line, String command) {
        for (IChatComponent piece : line.getSiblings()) {
            ClickEvent click = piece.getChatStyle()
                .getChatClickEvent();
            if (click != null && command.equals(click.getValue())) {
                return true;
            }
        }
        return false;
    }

    /** Права с ручным списком нод. */
    private static final class TestSubjects implements EconomySubjects {

        private final Set<String> held = new HashSet<>();
        private final Map<UUID, String> names = new LinkedHashMap<>();

        @Override
        public Optional<UUID> subjectOf(CommandContext context) {
            return Optional.empty();
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

    /** Валюты и больше ничего: показ читает из экономики только их. */
    private static final class TestEconomy implements EconomyService {

        private final List<CurrencyRecord> currencies = new ArrayList<>(Arrays.asList(CurrencyRecord.defaultCoin()));

        CurrencyRecord coin() {
            return currencies.get(0);
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
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean has(UUID player, long amount, String currencyId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<AccountView> account(UUID player) {
            throw new UnsupportedOperationException();
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
            throw new UnsupportedOperationException();
        }

        @Override
        public List<BalanceEntry> top(String currencyId, int page, int pageSize) {
            throw new UnsupportedOperationException();
        }
    }

    /** Консоль, которая помнит всё, что ей сказали. */
    private static final class Console implements ICommandSender {

        private final List<IChatComponent> heard = new ArrayList<>();

        @Override
        public String getCommandSenderName() {
            return "Server";
        }

        @Override
        public IChatComponent func_145748_c_() {
            return null;
        }

        @Override
        public void addChatMessage(IChatComponent message) {
            heard.add(message);
        }

        @Override
        public boolean canCommandSenderUseCommand(int permissionLevel, String command) {
            return true;
        }

        @Override
        public ChunkCoordinates getPlayerCoordinates() {
            return null;
        }

        @Override
        public World getEntityWorld() {
            return null;
        }
    }
}
