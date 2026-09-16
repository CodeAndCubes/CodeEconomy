package com.mrleonardos.codeeconomy.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntSupplier;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumChatFormatting;

import com.mrleonardos.codecore.api.command.ArgumentSpec;
import com.mrleonardos.codecore.api.command.CommandContext;
import com.mrleonardos.codecore.api.command.CommandNode;
import com.mrleonardos.codecore.platform.Senders;
import com.mrleonardos.codecore.platform.ServerTexts;
import com.mrleonardos.codecore.platform.present.Pages;
import com.mrleonardos.codecore.platform.present.PresentReplies;
import com.mrleonardos.codecore.platform.present.PresentTheme;
import com.mrleonardos.codecore.platform.present.RichCard;
import com.mrleonardos.codecore.platform.present.RichLine;
import com.mrleonardos.codeeconomy.api.Amounts;
import com.mrleonardos.codeeconomy.api.EconomyService;
import com.mrleonardos.codeeconomy.api.model.BalanceEntry;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;
import com.mrleonardos.codeeconomy.internal.EconomyConfig;
import com.mrleonardos.codeeconomy.internal.EconomyNodes;
import com.mrleonardos.codeeconomy.internal.command.EconomyMessages;
import com.mrleonardos.codeeconomy.internal.command.EconomyPresents;
import com.mrleonardos.codeeconomy.internal.command.EconomySubjects;
import com.mrleonardos.codeeconomy.internal.command.MaintenanceOutcome;

/**
 * Карточки и списки команд экономики из кирпичей ядра.
 *
 * <p>
 * Акцент мода жёлтый: деньги в этой игре золото, и жёлтый читается монетой на тёмном чате. Он отличим и
 * от золотого дефолта линейки, которым пользуются все сразу, и от зелёного регионов; слова кнопок жёлты
 * были и раньше, футер от заголовка отличают скобки и жирный шрифт.
 *
 * <p>
 * Что показывать, решают права: кнопка истории не появится без ноды на неё, строка топа не кликается
 * без ноды смотреть чужой баланс. Показать и не дать нажать значит позвать игрока выяснять, почему
 * нельзя.
 *
 * <p>
 * Цвет суммы в истории говорит направление относительно счёта: приход зелёным, расход красным. Статуса
 * в записи журнала нет, журнал хранит только проведённые операции, и выдумывать его перевод вывода не
 * станет; идентификатор операции уходит в подсказку наведения.
 *
 * <p>
 * Листание топа и истории собрано здесь, а не кирпичом {@code Pages} ядра: тот требует готовых строк
 * всей выдачи, а счетов на сервере до двухсот тысяч, записей истории до десяти тысяч. Строки строятся
 * только для среза страницы, общее число берётся из размера выдачи, кнопки листания берут переводы
 * ядра, консоли достаётся строка положения.
 */
final class EconomyPresent implements EconomyPresents {

    private static final PresentTheme THEME = PresentTheme.LINEUP.accent(EnumChatFormatting.YELLOW);

    private static final int TURN_GAP = 3;

    private final EconomyService economy;
    private final EconomyConfig config;
    private final EconomySubjects subjects;
    private final IntSupplier pageSize;

    EconomyPresent(EconomyService economy, EconomyConfig config, EconomySubjects subjects, IntSupplier pageSize) {
        this.economy = economy;
        this.config = config;
        this.subjects = subjects;
        this.pageSize = pageSize;
    }

    @Override
    public void branches(CommandContext context, List<CommandNode> visible) {
        send(context, branchesCard(visible));
    }

    @Override
    public void balance(CommandContext context, UUID player, boolean own, CurrencyRecord currency, long amount) {
        send(context, balanceCard(context, player, own, currency, amount));
    }

    @Override
    public void top(CommandContext context, CurrencyRecord currency, List<BalanceEntry> entries, int page) {
        ICommandSender target = sender(context);
        PresentReplies.send(target, topCard(target, context, currency, entries, page));
        if (!entries.isEmpty()) {
            PresentReplies.send(
                target,
                RichLine.of()
                    .label(EconomyMessages.BALTOP_NOTE));
        }
    }

    @Override
    public void history(CommandContext context, UUID player, boolean own, List<TransactionRecord> records, int page) {
        ICommandSender target = sender(context);
        PresentReplies.send(target, historyCard(target, player, own, records, page));
    }

    @Override
    public void importReport(CommandContext context, boolean apply, long moved, long rejected,
        List<MaintenanceOutcome.Row> rows) {
        RichCard card = RichCard.of(ServerTexts.format(EconomyMessages.CARD_IMPORT_TITLE), THEME);
        card.line(
            RichLine.of()
                .text(
                    ServerTexts.format(
                        apply ? EconomyMessages.IMPORT_DONE : EconomyMessages.IMPORT_DRY,
                        Long.valueOf(moved),
                        Long.valueOf(rejected))));
        for (MaintenanceOutcome.Row row : rows) {
            card.line(
                RichLine.of()
                    .muted(
                        ServerTexts.format(
                            row.key(),
                            row.arguments()
                                .toArray())));
        }
        send(context, card);
    }

    /**
     * Карточка веток: имя ведёт в ветку, рядом её вызов. Ветка с обязательным аргументом подставляется, а не
     * исполняется.
     */
    RichCard branchesCard(List<CommandNode> visible) {
        RichCard card = RichCard.of(ServerTexts.format(EconomyMessages.CARD_ECO_TITLE), THEME);
        if (visible.isEmpty()) {
            card.line(
                RichLine.of()
                    .label(EconomyMessages.CARD_ECO_NONE));
        }
        for (CommandNode branch : visible) {
            card.line(branchRow(branch));
        }
        return card;
    }

    /** Карточка баланса: поля счёта и футер с кнопками по правам отправителя. */
    RichCard balanceCard(CommandContext context, UUID player, boolean own, CurrencyRecord currency, long amount) {
        RichCard card = RichCard.of(name(player), THEME);
        card.field(EconomyMessages.CARD_CURRENCY, currency.displayName());
        card.field(EconomyMessages.CARD_BALANCE, Amounts.format(amount, currency));
        if (own) {
            ownBalance(card, context, currency);
        } else {
            foreignBalance(card, context, name(player));
        }
        return card;
    }

    private static RichLine branchRow(CommandNode branch) {
        RichLine row = RichLine.of()
            .accent(branch.name());
        if (needsArguments(branch)) {
            row.suggest("/eco " + branch.name() + " ");
        } else {
            row.run("/eco " + branch.name());
        }
        if (branch.usageKey() != null) {
            row.muted(" · ")
                .label(branch.usageKey());
        }
        return row;
    }

    private void ownBalance(RichCard card, CommandContext context, CurrencyRecord currency) {
        boolean mayPay = currency.payAllowed() && subjects.senderHas(context, EconomyNodes.PAY);
        if (mayPay) {
            card.line(
                RichLine.of()
                    .label(EconomyMessages.CARD_TRANSFER)
                    .muted(": ")
                    .value(transferRange(currency))
                    .hover(EconomyMessages.CARD_TRANSFER_HOVER));
        }
        if (subjects.senderHas(context, EconomyNodes.HISTORY)) {
            card.button(EconomyMessages.CARD_HISTORY, "/history", EconomyMessages.CARD_HISTORY_HOVER);
        }
        if (subjects.senderHas(context, EconomyNodes.BALTOP)) {
            card.button(EconomyMessages.CARD_TOP, topCommand(currency), EconomyMessages.CARD_TOP_HOVER);
        }
        if (mayPay) {
            card.suggestion(EconomyMessages.CARD_PAY, "/pay ", EconomyMessages.CARD_PAY_HOVER);
        }
    }

    private void foreignBalance(RichCard card, CommandContext context, String name) {
        if (subjects.senderHas(context, EconomyNodes.ADMIN_HISTORY)) {
            card.button(
                EconomyMessages.CARD_PLAYER_HISTORY,
                "/eco history " + name,
                EconomyMessages.CARD_PLAYER_HISTORY_HOVER);
        }
    }

    /**
     * Строки топа: место, имя и сумма. Имя кликается только с нодой смотреть чужой баланс, счёт без
     * имени показывает идентификатор и не кликается: аргумент игрока идентификатор не разбирает.
     */
    List<RichLine> topRows(CommandContext context, CurrencyRecord currency, List<BalanceEntry> entries, int first) {
        boolean mayLook = subjects.senderHas(context, EconomyNodes.BALANCE_OTHER);
        List<RichLine> rows = new ArrayList<>();
        int place = first;
        for (BalanceEntry entry : entries) {
            place++;
            String named = entry.name()
                .orElseGet(
                    () -> subjects.playerName(entry.player())
                        .orElse(null));
            RichLine row = RichLine.of()
                .muted(place + ". ")
                .accent(
                    named != null ? named
                        : entry.player()
                            .toString());
            if (named != null && mayLook) {
                row.run("/balance " + named + currencyTail(currency));
            }
            String amount = Amounts.format(entry.amount(), currency);
            row.hover(EconomyMessages.CARD_TOP_ROW_HOVER, amount)
                .muted(" · ")
                .value(amount);
            rows.add(row);
        }
        return rows;
    }

    /** Строки истории: вид с причиной в скобках, сумма цветом направления, причина и идентификатор рядом. */
    List<RichLine> historyRows(UUID player, List<TransactionRecord> records) {
        List<RichLine> rows = new ArrayList<>();
        for (TransactionRecord record : records) {
            boolean spent = record.from()
                .filter(player::equals)
                .isPresent();
            long amount = spent ? record.fromAfter()
                .orElse(0L)
                : record.toAfter()
                    .orElse(0L);
            CurrencyRecord currency = economy.currency(record.currencyId())
                .orElse(null);
            String shown = currency == null ? Long.toString(amount) : Amounts.format(amount, currency);
            RichLine row = RichLine.of()
                .label(EconomyMessages.historyKindKey(record.kind()))
                .muted(" (")
                .label(EconomyMessages.historyCauseKey(record.cause()))
                .muted("): ")
                .text(shown, spent ? EnumChatFormatting.RED : EnumChatFormatting.GREEN)
                .hover(EconomyMessages.CARD_HISTORY_ROW_HOVER, record.transactionId());
            if (record.reason()
                .isPresent()) {
                row.muted(" · ")
                    .label(
                        EconomyMessages.CARD_REASON,
                        record.reason()
                            .get());
            }
            rows.add(row);
        }
        return rows;
    }

    /** Пустой список это шапка и готовая строка, листать нечего. */
    private static void close(RichCard card, ICommandSender target, boolean empty, int page, int total, int size,
        String emptyKey, String commandTemplate) {
        if (empty) {
            card.line(
                RichLine.of()
                    .label(emptyKey));
            return;
        }
        int pages = pageCount(total, size);
        if (pages <= 1) {
            return;
        }
        card.line(target instanceof EntityPlayerMP ? turns(page, pages, commandTemplate) : position(page, pages));
    }

    /**
     * Страница топа: строки строятся только для среза, общее число берётся из размера выдачи.
     */
    RichCard topCard(ICommandSender target, CommandContext context, CurrencyRecord currency, List<BalanceEntry> entries,
        int number) {
        int size = pageSize.getAsInt();
        int page = checked(number, entries.size(), size);
        int from = (page - 1) * size;
        RichCard card = RichCard.of(THEME)
            .line(header(ServerTexts.format(EconomyMessages.CARD_TOP_TITLE, currency.displayName()), entries.size()));
        List<BalanceEntry> slice = entries.subList(from, Math.min(from + size, entries.size()));
        for (RichLine row : topRows(context, currency, slice, from)) {
            card.line(row);
        }
        close(
            card,
            target,
            entries.isEmpty(),
            page,
            entries.size(),
            size,
            EconomyMessages.BALTOP_EMPTY,
            topTurn(currency));
        return card;
    }

    /** Страница истории: тот же срез, тот же хвост, своим шаблоном листания. */
    RichCard historyCard(ICommandSender target, UUID player, boolean own, List<TransactionRecord> records, int number) {
        int size = pageSize.getAsInt();
        int page = checked(number, records.size(), size);
        int from = (page - 1) * size;
        RichCard card = RichCard.of(THEME)
            .line(header(ServerTexts.format(EconomyMessages.CARD_HISTORY_TITLE, name(player)), records.size()));
        List<TransactionRecord> slice = records.subList(from, Math.min(from + size, records.size()));
        for (RichLine row : historyRows(player, slice)) {
            card.line(row);
        }
        close(
            card,
            target,
            records.isEmpty(),
            page,
            records.size(),
            size,
            EconomyMessages.HISTORY_EMPTY,
            historyTurn(player, own));
        return card;
    }

    private static RichLine header(String title, int total) {
        return RichLine.of()
            .title(title)
            .muted(" · ")
            .label(Pages.TOTAL, total);
    }

    /** Футер листания для игрока: кнопки, положение соседней страницы в подсказке. */
    static RichLine turns(int number, int pages, String commandTemplate) {
        RichLine line = RichLine.of();
        if (number > 1) {
            line.button(ServerTexts.format(Pages.BACK), pageCommand(commandTemplate, number - 1))
                .hover(Pages.PAGE, number - 1, pages)
                .spacing(TURN_GAP);
        }
        line.muted(number + "/" + pages);
        if (number < pages) {
            line.spacing(TURN_GAP)
                .button(ServerTexts.format(Pages.NEXT), pageCommand(commandTemplate, number + 1))
                .hover(Pages.PAGE, number + 1, pages);
        }
        return line;
    }

    /** Строка положения для консоли и RCON: кликов там нет. */
    private static RichLine position(int number, int pages) {
        return RichLine.of()
            .label(Pages.PAGE, number, pages);
    }

    private static int pageCount(int total, int size) {
        return (total + size - 1) / size;
    }

    private static int checked(int number, int total, int size) {
        int pages = pageCount(total, size);
        return pages <= 1 ? 1 : Math.max(1, Math.min(number, pages));
    }

    /** Номер страницы в шаблон команды: первый {@code %d} принимает номер, прочие знаки процента не тронуты. */
    private static String pageCommand(String commandTemplate, int number) {
        int slot = commandTemplate.indexOf("%d");
        if (slot < 0) {
            throw new IllegalArgumentException("Page command template needs %d for the page number");
        }
        return commandTemplate.substring(0, slot) + number + commandTemplate.substring(slot + 2);
    }

    private String transferRange(CurrencyRecord currency) {
        return ServerTexts.format(
            EconomyMessages.CARD_TRANSFER_RANGE,
            Amounts.format(config.minTransfer(), currency),
            Amounts.format(config.maxTransfer(), currency));
    }

    private String topCommand(CurrencyRecord currency) {
        return economy.defaultCurrencyId()
            .equals(currency.id()) ? "/baltop" : "/baltop 1 " + currency.id();
    }

    /** Шаблон листания топа: валюте не по умолчанию нужен явный аргумент в каждой странице. */
    String topTurn(CurrencyRecord currency) {
        return "/baltop %d" + currencyTail(currency);
    }

    /** Шаблон листания истории: чужая история листается командой с именем игрока. */
    String historyTurn(UUID player, boolean own) {
        return own ? "/history %d" : "/eco history " + name(player) + " %d";
    }

    /** Хвост команды с явной валютой: валюте по умолчанию хвост не нужен. */
    private String currencyTail(CurrencyRecord currency) {
        return economy.defaultCurrencyId()
            .equals(currency.id()) ? "" : " " + currency.id();
    }

    private static boolean needsArguments(CommandNode branch) {
        for (ArgumentSpec argument : branch.arguments()) {
            if (!argument.optional()) {
                return true;
            }
        }
        return false;
    }

    private String name(UUID player) {
        return subjects.playerName(player)
            .orElse(player.toString());
    }

    private static ICommandSender sender(CommandContext context) {
        return Senders.platform(context.caller());
    }

    private static void send(CommandContext context, RichCard card) {
        PresentReplies.send(sender(context), card);
    }
}
