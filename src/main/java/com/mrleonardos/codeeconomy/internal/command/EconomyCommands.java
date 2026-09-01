package com.mrleonardos.codeeconomy.internal.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.IntSupplier;

import com.mrleonardos.codecore.api.command.ArgumentType;
import com.mrleonardos.codecore.api.command.ArgumentTypes;
import com.mrleonardos.codecore.api.command.CommandContext;
import com.mrleonardos.codecore.api.command.CommandMessages;
import com.mrleonardos.codecore.api.command.CommandNode;
import com.mrleonardos.codecore.api.command.CommandService;
import com.mrleonardos.codeeconomy.api.Amounts;
import com.mrleonardos.codeeconomy.api.CurrencyIds;
import com.mrleonardos.codeeconomy.api.EconomyService;
import com.mrleonardos.codeeconomy.api.model.BalanceEntry;
import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;
import com.mrleonardos.codeeconomy.api.model.TransferRequest;
import com.mrleonardos.codeeconomy.api.model.TransferResult;
import com.mrleonardos.codeeconomy.internal.EconomyNodes;

public final class EconomyCommands {

    private static final String PLAYER_ARGUMENT = "player";
    private static final String AMOUNT_ARGUMENT = "amount";
    private static final String CURRENCY_ARGUMENT = "currency";
    private static final String PAGE_ARGUMENT = "page";
    private static final String STATE_ARGUMENT = "state";
    private static final String FORMAT_ARGUMENT = "format";
    private static final String FILE_ARGUMENT = "file";
    private static final String FLAGS_ARGUMENT = "flags";

    private static final String APPLY_FLAG = "--apply";
    private static final String TRANSACTION_PREFIX = "cmd:";
    private static final String EMPTY_MARKER = "-";

    private static final int MIN_PAGE = 1;
    private static final int MAX_PAGE = 1000000;
    private static final int MAX_FORMAT_LENGTH = 16;
    private static final int MAX_FILE_LENGTH = 128;

    private final EconomyService economy;
    private final EconomyMutations mutations;
    private final EconomyMaintenance maintenance;
    private final EconomyArguments arguments;
    private final EconomySubjects subjects;
    private final IntSupplier pageSize;
    private final List<CommandNode> adminBranches;

    public EconomyCommands(EconomyService economy, EconomyMutations mutations, EconomyMaintenance maintenance,
        EconomyArguments arguments, EconomySubjects subjects, IntSupplier pageSize) {
        this.economy = economy;
        this.mutations = mutations;
        this.maintenance = maintenance;
        this.arguments = arguments;
        this.subjects = subjects;
        this.pageSize = pageSize;
        this.adminBranches = Arrays.asList(
            give(),
            take(),
            set(),
            reset(),
            adminHistory(),
            freeze(),
            verify(),
            checkpoint(),
            compact(),
            unlock(),
            importBranch());
    }

    public void register(CommandService commands) {
        for (CommandNode root : roots()) {
            commands.register(root);
        }
    }

    public List<CommandNode> roots() {
        return Arrays.asList(balance(), pay(), baltop(), history(), eco());
    }

    private CommandNode balance() {
        CommandNode node = CommandNode.literal("balance")
            .alias("bal", "money")
            .permission(EconomyNodes.BALANCE)
            .usage(EconomyMessages.USAGE_BALANCE)
            .optionalArg(PLAYER_ARGUMENT, arguments.player())
            .executes(this::balance);
        return withCurrency(node);
    }

    private CommandNode pay() {
        CommandNode node = CommandNode.literal("pay")
            .permission(EconomyNodes.PAY)
            .usage(EconomyMessages.USAGE_PAY)
            .arg(PLAYER_ARGUMENT, arguments.player())
            .arg(AMOUNT_ARGUMENT, ArgumentTypes.word())
            .executes(this::pay);
        return withCurrency(node);
    }

    private CommandNode baltop() {
        CommandNode node = CommandNode.literal("baltop")
            .permission(EconomyNodes.BALTOP)
            .usage(EconomyMessages.USAGE_BALTOP)
            .optionalArg(PAGE_ARGUMENT, pageArgument())
            .executes(this::baltop);
        return withCurrency(node);
    }

    private CommandNode history() {
        return CommandNode.literal("history")
            .permission(EconomyNodes.HISTORY)
            .usage(EconomyMessages.USAGE_HISTORY)
            .optionalArg(PAGE_ARGUMENT, pageArgument())
            .executes(this::ownHistory);
    }

    private CommandNode eco() {
        CommandNode root = CommandNode.literal("eco")
            .usage(EconomyMessages.USAGE_ECO)
            .executes(this::branches);
        for (CommandNode branch : adminBranches) {
            root.child(branch);
        }
        return root;
    }

    private CommandNode give() {
        CommandNode node = CommandNode.literal("give")
            .permission(EconomyNodes.ADMIN_GIVE)
            .usage(EconomyMessages.USAGE_ECO_GIVE)
            .arg(PLAYER_ARGUMENT, arguments.player())
            .arg(AMOUNT_ARGUMENT, ArgumentTypes.word())
            .executes(this::give);
        return withCurrency(node);
    }

    private CommandNode take() {
        CommandNode node = CommandNode.literal("take")
            .permission(EconomyNodes.ADMIN_TAKE)
            .usage(EconomyMessages.USAGE_ECO_TAKE)
            .arg(PLAYER_ARGUMENT, arguments.player())
            .arg(AMOUNT_ARGUMENT, ArgumentTypes.word())
            .executes(this::take);
        return withCurrency(node);
    }

    private CommandNode set() {
        CommandNode node = CommandNode.literal("set")
            .permission(EconomyNodes.ADMIN_SET)
            .usage(EconomyMessages.USAGE_ECO_SET)
            .arg(PLAYER_ARGUMENT, arguments.player())
            .arg(AMOUNT_ARGUMENT, ArgumentTypes.word())
            .executes(this::set);
        return withCurrency(node);
    }

    private CommandNode reset() {
        CommandNode node = CommandNode.literal("reset")
            .permission(EconomyNodes.ADMIN_RESET)
            .usage(EconomyMessages.USAGE_ECO_RESET)
            .arg(PLAYER_ARGUMENT, arguments.player())
            .executes(this::reset);
        return withCurrency(node);
    }

    private CommandNode adminHistory() {
        return CommandNode.literal("history")
            .permission(EconomyNodes.ADMIN_HISTORY)
            .usage(EconomyMessages.USAGE_ECO_HISTORY)
            .arg(PLAYER_ARGUMENT, arguments.player())
            .optionalArg(PAGE_ARGUMENT, pageArgument())
            .executes(this::adminHistory);
    }

    private CommandNode freeze() {
        return CommandNode.literal("freeze")
            .permission(EconomyNodes.ADMIN_FREEZE)
            .usage(EconomyMessages.USAGE_ECO_FREEZE)
            .arg(PLAYER_ARGUMENT, arguments.player())
            .arg(STATE_ARGUMENT, ArgumentTypes.enumOf(FreezeState.class))
            .executes(this::freeze);
    }

    private CommandNode verify() {
        return CommandNode.literal("verify")
            .permission(EconomyNodes.ADMIN_VERIFY)
            .usage(EconomyMessages.USAGE_ECO_VERIFY)
            .executes(this::verify);
    }

    private CommandNode checkpoint() {
        return CommandNode.literal("checkpoint")
            .permission(EconomyNodes.ADMIN_CHECKPOINT)
            .usage(EconomyMessages.USAGE_ECO_CHECKPOINT)
            .executes(this::checkpoint);
    }

    private CommandNode compact() {
        return CommandNode.literal("compact")
            .permission(EconomyNodes.ADMIN_COMPACT)
            .usage(EconomyMessages.USAGE_ECO_COMPACT)
            .executes(this::compact);
    }

    private CommandNode unlock() {
        return CommandNode.literal("unlock")
            .permission(EconomyNodes.ADMIN_UNLOCK)
            .usage(EconomyMessages.USAGE_ECO_UNLOCK)
            .executes(this::unlock);
    }

    private CommandNode importBranch() {
        return CommandNode.literal("import")
            .permission(EconomyNodes.ADMIN_IMPORT)
            .usage(EconomyMessages.USAGE_ECO_IMPORT)
            .arg(FORMAT_ARGUMENT, ArgumentTypes.word())
            .optionalArg(FILE_ARGUMENT, ArgumentTypes.word())
            .optionalArg(FLAGS_ARGUMENT, ArgumentTypes.text())
            .executes(this::importBalances);
    }

    private CommandNode withCurrency(CommandNode node) {
        if (visibleCurrencies() > 1) {
            node.optionalArg(CURRENCY_ARGUMENT, arguments.currency());
        }
        return node;
    }

    private int visibleCurrencies() {
        int visible = 0;
        for (CurrencyRecord currency : economy.currencies()) {
            if (currency.visible()) {
                visible++;
            }
        }
        return visible;
    }

    private static ArgumentType<?> pageArgument() {
        return ArgumentTypes.integer(MIN_PAGE, MAX_PAGE);
    }

    /**
     * Есть ли у отправителя право уводить счёт до {@code negativeFloor}.
     *
     * <p>
     * Спрашивается здесь, а не в движке: у консоли нет uuid, и по пустому автору движок обхода не
     * даёт. Иначе любой чужой мод получал бы его, просто не заполнив поле actor.
     */
    private boolean floorBypass(CommandContext context) {
        return subjects.senderHas(context, EconomyNodes.BYPASS_MIN_BALANCE);
    }

    private void branches(CommandContext context) {
        List<String> visible = new ArrayList<>();
        for (CommandNode branch : adminBranches) {
            if (subjects.senderHas(context, branch.permissionNode())) {
                visible.add(branch.name());
            }
        }
        context.reply(EconomyMessages.ECO_BRANCHES, visible.isEmpty() ? EMPTY_MARKER : join(visible));
    }

    private void balance(CommandContext context) {
        UUID self = subjects.subjectOf(context)
            .orElse(null);
        UUID target = context.has(PLAYER_ARGUMENT) ? context.get(PLAYER_ARGUMENT) : self;
        if (target == null) {
            context.replyError(CommandMessages.PLAYERS_ONLY);
            return;
        }
        CurrencyRecord currency = currency(context);
        if (currency == null) {
            return;
        }
        if (!target.equals(self) && !subjects.senderHas(context, EconomyNodes.BALANCE_OTHER)) {
            context.replyError(CommandMessages.NO_PERMISSION);
            return;
        }
        long amount = economy.balance(target, currency.id());
        if (target.equals(self)) {
            context.reply(EconomyMessages.BALANCE_SELF, Amounts.format(amount, currency));
            return;
        }
        context.reply(EconomyMessages.BALANCE_SHOW, name(target), Amounts.format(amount, currency));
    }

    private void pay(CommandContext context) {
        UUID self = subjects.subjectOf(context)
            .orElse(null);
        if (self == null) {
            context.replyError(CommandMessages.PLAYERS_ONLY);
            return;
        }
        UUID target = context.get(PLAYER_ARGUMENT);
        CurrencyRecord currency = currency(context);
        if (currency == null) {
            return;
        }
        OptionalLong amount = amount(context, currency);
        if (!amount.isPresent()) {
            return;
        }
        TransferRequest request = TransferRequest
            .transfer(self, target, amount.getAsLong(), currency.id(), transactionId(), self, null);
        TransferResult result = mutations.apply(request, ChangeCause.COMMAND, floorBypass(context));
        if (result.applied()) {
            context.reply(
                EconomyMessages.PAY_SENT,
                Amounts.format(amount.getAsLong(), currency),
                name(target),
                shown(result.toAfter(), target, currency));
            return;
        }
        failure(context, result, currency, self, target, amount.getAsLong());
    }

    private void give(CommandContext context) {
        UUID actor = subjects.subjectOf(context)
            .orElse(null);
        UUID target = context.get(PLAYER_ARGUMENT);
        CurrencyRecord currency = currency(context);
        if (currency == null) {
            return;
        }
        OptionalLong amount = amount(context, currency);
        if (!amount.isPresent()) {
            return;
        }
        TransferRequest request = TransferRequest
            .deposit(target, amount.getAsLong(), currency.id(), transactionId(), actor, null);
        TransferResult result = mutations.apply(request, ChangeCause.COMMAND, floorBypass(context));
        if (result.applied()) {
            context.reply(
                EconomyMessages.GIVE_DONE,
                Amounts.format(amount.getAsLong(), currency),
                name(target),
                shown(result.toAfter(), target, currency));
            return;
        }
        failure(context, result, currency, target, target, amount.getAsLong());
    }

    private void take(CommandContext context) {
        UUID actor = subjects.subjectOf(context)
            .orElse(null);
        UUID target = context.get(PLAYER_ARGUMENT);
        CurrencyRecord currency = currency(context);
        if (currency == null) {
            return;
        }
        OptionalLong amount = amount(context, currency);
        if (!amount.isPresent()) {
            return;
        }
        TransferRequest request = TransferRequest
            .withdraw(target, amount.getAsLong(), currency.id(), transactionId(), actor, null);
        TransferResult result = mutations.apply(request, ChangeCause.COMMAND, floorBypass(context));
        if (result.applied()) {
            context.reply(
                EconomyMessages.TAKE_DONE,
                Amounts.format(amount.getAsLong(), currency),
                name(target),
                shown(result.fromAfter(), target, currency));
            return;
        }
        failure(context, result, currency, target, target, amount.getAsLong());
    }

    private void set(CommandContext context) {
        UUID actor = subjects.subjectOf(context)
            .orElse(null);
        UUID target = context.get(PLAYER_ARGUMENT);
        CurrencyRecord currency = currency(context);
        if (currency == null) {
            return;
        }
        OptionalLong amount = amount(context, currency);
        if (!amount.isPresent()) {
            return;
        }
        TransferRequest request = TransferRequest
            .set(target, amount.getAsLong(), currency.id(), transactionId(), actor, null);
        TransferResult result = mutations.apply(request, ChangeCause.COMMAND, floorBypass(context));
        if (result.applied()) {
            context.reply(EconomyMessages.SET_DONE, name(target), shown(result.toAfter(), target, currency));
            return;
        }
        failure(context, result, currency, target, target, amount.getAsLong());
    }

    private void reset(CommandContext context) {
        UUID actor = subjects.subjectOf(context)
            .orElse(null);
        UUID target = context.get(PLAYER_ARGUMENT);
        CurrencyRecord currency = currency(context);
        if (currency == null) {
            return;
        }
        TransferRequest request = TransferRequest.reset(target, currency.id(), transactionId(), actor, null);
        TransferResult result = mutations.apply(request, ChangeCause.COMMAND, floorBypass(context));
        if (result.applied()) {
            context.reply(EconomyMessages.RESET_DONE, name(target), shown(result.toAfter(), target, currency));
            return;
        }
        failure(context, result, currency, target, target, 0L);
    }

    private void ownHistory(CommandContext context) {
        UUID self = subjects.subjectOf(context)
            .orElse(null);
        if (self == null) {
            context.replyError(CommandMessages.PLAYERS_ONLY);
            return;
        }
        showHistory(context, self, page(context));
    }

    private void adminHistory(CommandContext context) {
        UUID target = context.get(PLAYER_ARGUMENT);
        showHistory(context, target, page(context));
    }

    private void showHistory(CommandContext context, UUID target, int page) {
        List<TransactionRecord> records = economy.history(target, page - 1, pageSize.getAsInt());
        if (records.isEmpty()) {
            context.reply(EconomyMessages.HISTORY_EMPTY);
            return;
        }
        context.reply(EconomyMessages.HISTORY_HEADER, page);
        for (TransactionRecord record : records) {
            context.reply(EconomyMessages.historyRowKey(record.kind(), record.cause()), shownAmount(record, target));
            if (record.reason()
                .isPresent()) {
                context.reply(
                    EconomyMessages.HISTORY_REASON,
                    record.reason()
                        .get());
            }
        }
    }

    private void baltop(CommandContext context) {
        CurrencyRecord currency = currency(context);
        if (currency == null) {
            return;
        }
        int page = page(context);
        List<BalanceEntry> entries = economy.top(currency.id(), page - 1, pageSize.getAsInt());
        if (entries.isEmpty()) {
            context.reply(EconomyMessages.BALTOP_EMPTY);
            return;
        }
        context.reply(EconomyMessages.BALTOP_HEADER, currency.displayName(), page);
        context.reply(EconomyMessages.BALTOP_NOTE);
        int place = (page - 1) * pageSize.getAsInt();
        for (BalanceEntry entry : entries) {
            place++;
            context.reply(
                EconomyMessages.BALTOP_ROW,
                place,
                entry.name()
                    .orElseGet(() -> name(entry.player())),
                Amounts.format(entry.amount(), currency));
        }
    }

    private void freeze(CommandContext context) {
        UUID actor = subjects.subjectOf(context)
            .orElse(null);
        UUID target = context.get(PLAYER_ARGUMENT);
        FreezeState state = context.get(STATE_ARGUMENT);
        MaintenanceOutcome outcome = maintenance.freeze(target, state == FreezeState.ON, actor, transactionId());
        if (outcome.successful()) {
            context
                .reply(state == FreezeState.ON ? EconomyMessages.FREEZE_ON : EconomyMessages.FREEZE_OFF, name(target));
            return;
        }
        replyFailure(context, outcome, name(target));
    }

    private void verify(CommandContext context) {
        maintenance.verify();
        context.reply(EconomyMessages.VERIFY_STARTED);
    }

    private void checkpoint(CommandContext context) {
        MaintenanceOutcome outcome = maintenance.checkpoint();
        if (!outcome.successful()) {
            replyFailure(context, outcome);
            return;
        }
        context.reply(
            written(outcome) ? EconomyMessages.CHECKPOINT_DONE : EconomyMessages.CHECKPOINT_SKIPPED,
            Long.valueOf(outcome.number()));
    }

    private void compact(CommandContext context) {
        replyWithNumber(context, maintenance.compact(), EconomyMessages.COMPACT_DONE);
    }

    private void unlock(CommandContext context) {
        MaintenanceOutcome outcome = maintenance.unlock();
        if (!outcome.successful()) {
            replyFailure(context, outcome);
            return;
        }
        context.reply(outcome.number() > 0L ? EconomyMessages.UNLOCK_DONE : EconomyMessages.UNLOCK_IDLE);
    }

    /**
     * Правда ли снимок действительно записан. Обслуживание отвечает признаком в значениях, потому что
     * ответ «граница та же, писать было нечего» и ответ «файл переписан» это разные новости для
     * администратора.
     */
    private static boolean written(MaintenanceOutcome outcome) {
        return outcome.successful() && !outcome.values()
            .isEmpty()
            && Boolean.TRUE.equals(
                outcome.values()
                    .get(0));
    }

    private void importBalances(CommandContext context) {
        String format = context.get(FORMAT_ARGUMENT);
        String file = context.getOrDefault(FILE_ARGUMENT, "");
        if (format.length() > MAX_FORMAT_LENGTH || file.length() > MAX_FILE_LENGTH) {
            context.replyError(EconomyMessages.FAILURE_INVALID_REQUEST);
            return;
        }
        Boolean apply = flag(context);
        if (apply == null) {
            return;
        }
        MaintenanceOutcome outcome = maintenance.importBalances(CurrencyIds.normalize(format), file, apply);
        replyWithValues(context, outcome, apply ? EconomyMessages.IMPORT_DONE : EconomyMessages.IMPORT_DRY);
    }

    private void replyWithNumber(CommandContext context, MaintenanceOutcome outcome, String successKey) {
        if (outcome.successful()) {
            List<Object> arguments = new ArrayList<>();
            arguments.add(Long.valueOf(outcome.number()));
            arguments.addAll(outcome.values());
            context.reply(successKey, arguments.toArray());
            replyRows(context, outcome);
            return;
        }
        replyFailure(context, outcome);
    }

    private void replyWithValues(CommandContext context, MaintenanceOutcome outcome, String successKey) {
        if (outcome.successful()) {
            context.reply(
                successKey,
                outcome.values()
                    .toArray());
            replyRows(context, outcome);
            return;
        }
        replyFailure(context, outcome);
    }

    private void replyRows(CommandContext context, MaintenanceOutcome outcome) {
        for (MaintenanceOutcome.Row row : outcome.rows()) {
            context.reply(
                row.key(),
                row.arguments()
                    .toArray());
        }
    }

    private void replyFailure(CommandContext context, MaintenanceOutcome outcome) {
        context.replyError(
            EconomyMessages.failureKey(
                outcome.code()
                    .get()));
    }

    private void replyFailure(CommandContext context, MaintenanceOutcome outcome, Object subject) {
        context.replyError(
            EconomyMessages.failureKey(
                outcome.code()
                    .get()),
            subject);
    }

    private void failure(CommandContext context, TransferResult result, CurrencyRecord currency, UUID subject,
        UUID target, long amount) {
        switch (result.code()) {
            case INSUFFICIENT:
                context.replyError(
                    EconomyMessages.FAILURE_INSUFFICIENT,
                    Amounts.format(economy.balance(subject, currency.id()), currency),
                    Amounts.format(amount, currency));
                return;
            case ABOVE_CEILING:
                context
                    .replyError(EconomyMessages.FAILURE_ABOVE_CEILING, Amounts.format(currency.maxBalance(), currency));
                return;
            case BELOW_FLOOR:
                context
                    .replyError(EconomyMessages.FAILURE_BELOW_FLOOR, Amounts.format(currency.minBalance(), currency));
                return;
            case UNKNOWN_CURRENCY:
                context.replyError(EconomyMessages.FAILURE_UNKNOWN_CURRENCY, currency.id());
                return;
            case UNKNOWN_PLAYER:
            case ACCOUNT_FROZEN:
                context.replyError(EconomyMessages.failureKey(result.code()), name(target));
                return;
            case PAY_DISABLED:
                context.replyError(EconomyMessages.FAILURE_PAY_DISABLED, currency.displayName());
                return;
            default:
                context.replyError(EconomyMessages.failureKey(result.code()));
        }
    }

    private CurrencyRecord currency(CommandContext context) {
        String requested = context.getOrDefault(CURRENCY_ARGUMENT, null);
        String id = requested != null ? CurrencyIds.normalize(requested) : economy.defaultCurrencyId();
        CurrencyRecord currency = economy.currency(id)
            .orElse(null);
        if (currency == null) {
            context.replyError(EconomyMessages.FAILURE_UNKNOWN_CURRENCY, requested == null ? id : requested);
        }
        return currency;
    }

    private OptionalLong amount(CommandContext context, CurrencyRecord currency) {
        String raw = context.get(AMOUNT_ARGUMENT);
        OptionalLong parsed = AmountArgument.parse(raw, currency);
        if (!parsed.isPresent()) {
            context.replyError(EconomyMessages.FAILURE_BAD_AMOUNT, raw);
        }
        return parsed;
    }

    private int page(CommandContext context) {
        return context.getOrDefault(PAGE_ARGUMENT, MIN_PAGE);
    }

    private Boolean flag(CommandContext context) {
        String tail = context.getOrDefault(FLAGS_ARGUMENT, "");
        boolean apply = false;
        for (String flag : tail.split(" ")) {
            if (flag.isEmpty()) {
                continue;
            }
            if (APPLY_FLAG.equals(flag)) {
                apply = true;
            } else {
                context.replyError(EconomyMessages.FAILURE_INVALID_REQUEST, flag);
                return null;
            }
        }
        return apply;
    }

    private String shown(OptionalLong after, UUID player, CurrencyRecord currency) {
        long amount = after.isPresent() ? after.getAsLong() : economy.balance(player, currency.id());
        return Amounts.format(amount, currency);
    }

    private String shownAmount(TransactionRecord record, UUID target) {
        boolean taken = record.from()
            .filter(target::equals)
            .isPresent();
        long amount = taken ? record.fromAfter()
            .orElse(0L)
            : record.toAfter()
                .orElse(0L);
        CurrencyRecord currency = economy.currency(record.currencyId())
            .orElse(null);
        return currency == null ? Long.toString(amount) : Amounts.format(amount, currency);
    }

    private String name(UUID player) {
        return subjects.playerName(player)
            .orElse(player.toString());
    }

    private static String join(List<String> parts) {
        StringBuilder joined = new StringBuilder();
        for (String part : parts) {
            if (joined.length() > 0) {
                joined.append(", ");
            }
            joined.append(part);
        }
        return joined.toString();
    }

    private static String transactionId() {
        return TRANSACTION_PREFIX + UUID.randomUUID();
    }

    private enum FreezeState {

        ON,

        OFF
    }
}
