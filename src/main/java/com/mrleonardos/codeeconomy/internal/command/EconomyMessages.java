package com.mrleonardos.codeeconomy.internal.command;

import java.util.Locale;

import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.ResultCode;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;

public final class EconomyMessages {

    public static final String USAGE_ROOT = "codeeconomy.command.usage";
    public static final String USAGE_BALANCE = "codeeconomy.command.usage.balance";
    public static final String USAGE_PAY = "codeeconomy.command.usage.pay";
    public static final String USAGE_BALTOP = "codeeconomy.command.usage.baltop";
    public static final String USAGE_HISTORY = "codeeconomy.command.usage.history";
    public static final String USAGE_ECO = "codeeconomy.command.usage.eco";
    public static final String USAGE_ECO_GIVE = "codeeconomy.command.usage.eco.give";
    public static final String USAGE_ECO_TAKE = "codeeconomy.command.usage.eco.take";
    public static final String USAGE_ECO_SET = "codeeconomy.command.usage.eco.set";
    public static final String USAGE_ECO_RESET = "codeeconomy.command.usage.eco.reset";
    public static final String USAGE_ECO_HISTORY = "codeeconomy.command.usage.eco.history";
    public static final String USAGE_ECO_FREEZE = "codeeconomy.command.usage.eco.freeze";
    public static final String USAGE_ECO_VERIFY = "codeeconomy.command.usage.eco.verify";
    public static final String USAGE_ECO_CHECKPOINT = "codeeconomy.command.usage.eco.checkpoint";
    public static final String USAGE_ECO_COMPACT = "codeeconomy.command.usage.eco.compact";
    public static final String USAGE_ECO_IMPORT = "codeeconomy.command.usage.eco.import";

    public static final String ECO_BRANCHES = "codeeconomy.eco.branches";

    public static final String BALANCE_SELF = "codeeconomy.balance.self";
    public static final String BALANCE_SHOW = "codeeconomy.balance.show";

    public static final String PAY_SENT = "codeeconomy.pay.sent";

    public static final String GIVE_DONE = "codeeconomy.admin.give.done";
    public static final String TAKE_DONE = "codeeconomy.admin.take.done";
    public static final String SET_DONE = "codeeconomy.admin.set.done";
    public static final String RESET_DONE = "codeeconomy.admin.reset.done";
    public static final String FREEZE_ON = "codeeconomy.admin.freeze.on";
    public static final String FREEZE_OFF = "codeeconomy.admin.freeze.off";

    public static final String VERIFY_STARTED = "codeeconomy.admin.verify.started";
    public static final String CHECKPOINT_DONE = "codeeconomy.admin.checkpoint.done";
    public static final String COMPACT_DONE = "codeeconomy.admin.compact.done";
    public static final String IMPORT_DONE = "codeeconomy.admin.import.done";
    public static final String IMPORT_DRY = "codeeconomy.admin.import.dry";
    public static final String IMPORT_SKIP = "codeeconomy.admin.import.skip";

    public static final String BALTOP_HEADER = "codeeconomy.baltop.header";
    public static final String BALTOP_NOTE = "codeeconomy.baltop.note";
    public static final String BALTOP_ROW = "codeeconomy.baltop.row";
    public static final String BALTOP_EMPTY = "codeeconomy.baltop.empty";

    public static final String HISTORY_HEADER = "codeeconomy.history.header";
    public static final String HISTORY_EMPTY = "codeeconomy.history.empty";
    public static final String HISTORY_REASON = "codeeconomy.history.reason";

    public static final String FAILURE_BAD_AMOUNT = "codeeconomy.failure.bad_amount";
    public static final String FAILURE_UNKNOWN_CURRENCY = "codeeconomy.failure.unknown_currency";
    public static final String FAILURE_UNKNOWN_PLAYER = "codeeconomy.failure.unknown_player";
    public static final String FAILURE_SAME_ACCOUNT = "codeeconomy.failure.same_account";
    public static final String FAILURE_PAY_DISABLED = "codeeconomy.failure.pay_disabled";
    public static final String FAILURE_FROZEN = "codeeconomy.failure.frozen";
    public static final String FAILURE_INSUFFICIENT = "codeeconomy.failure.insufficient";
    public static final String FAILURE_ABOVE_CEILING = "codeeconomy.failure.above_ceiling";
    public static final String FAILURE_BELOW_FLOOR = "codeeconomy.failure.below_floor";
    public static final String FAILURE_GUARD = "codeeconomy.failure.guard";
    public static final String FAILURE_READONLY = "codeeconomy.failure.readonly";
    public static final String FAILURE_STORE = "codeeconomy.failure.store";
    public static final String FAILURE_INVALID_REQUEST = "codeeconomy.failure.invalid_request";

    private EconomyMessages() {}

    public static String failureKey(ResultCode code) {
        switch (code) {
            case BAD_AMOUNT:
                return FAILURE_BAD_AMOUNT;
            case UNKNOWN_CURRENCY:
                return FAILURE_UNKNOWN_CURRENCY;
            case UNKNOWN_PLAYER:
                return FAILURE_UNKNOWN_PLAYER;
            case SAME_ACCOUNT:
                return FAILURE_SAME_ACCOUNT;
            case PAY_DISABLED:
                return FAILURE_PAY_DISABLED;
            case ACCOUNT_FROZEN:
                return FAILURE_FROZEN;
            case INSUFFICIENT:
                return FAILURE_INSUFFICIENT;
            case ABOVE_CEILING:
                return FAILURE_ABOVE_CEILING;
            case BELOW_FLOOR:
                return FAILURE_BELOW_FLOOR;
            case GUARD_VETO:
                return FAILURE_GUARD;
            case STORE_FAILURE:
                return FAILURE_STORE;
            case READONLY:
                return FAILURE_READONLY;
            default:
                return FAILURE_INVALID_REQUEST;
        }
    }

    public static String historyRowKey(TransactionRecord.Kind kind, ChangeCause cause) {
        return "codeeconomy.history." + kind.name()
            .toLowerCase(Locale.ROOT)
            + "."
            + cause.name()
                .toLowerCase(Locale.ROOT);
    }
}
