package com.mrleonardos.codeeconomy.internal;

/**
 * Ноды прав и ключи меты экономики: один источник, движок, команды и {@code perms.md} берут отсюда.
 *
 * <p>
 * Ноды и ключи перевода живут в разных ветках имён: ноды это {@code codeeconomy.<команда>}, тексты это
 * {@code codeeconomy.command.*} и {@code codeeconomy.message.*}. Выдавая права по маске, администратор
 * не должен натыкаться на строки перевода.
 */
public final class EconomyNodes {

    /** {@code /balance}: свой баланс. */
    public static final String BALANCE = "codeeconomy.balance";

    /** {@code /balance <ник>}: чужой баланс. */
    public static final String BALANCE_OTHER = "codeeconomy.balance.other";

    /** {@code /pay}: перевод другому игроку. */
    public static final String PAY = "codeeconomy.pay";

    /** {@code /baltop}: топ по валюте. */
    public static final String BALTOP = "codeeconomy.baltop";

    /** {@code /history}: свои записи журнала. */
    public static final String HISTORY = "codeeconomy.history";

    /** {@code /eco give}: выдача денег. */
    public static final String ADMIN_GIVE = "codeeconomy.admin.give";

    /** {@code /eco take}: снятие денег. */
    public static final String ADMIN_TAKE = "codeeconomy.admin.take";

    /** {@code /eco set}: установка баланса. */
    public static final String ADMIN_SET = "codeeconomy.admin.set";

    /** {@code /eco reset}: возврат к стартовому балансу. */
    public static final String ADMIN_RESET = "codeeconomy.admin.reset";

    /** {@code /eco history}: записи журнала любого игрока. */
    public static final String ADMIN_HISTORY = "codeeconomy.admin.history";

    /** {@code /eco freeze}: заморозка счёта. */
    public static final String ADMIN_FREEZE = "codeeconomy.admin.freeze";

    /** {@code /eco verify}: сверка журнала со счетами. */
    public static final String ADMIN_VERIFY = "codeeconomy.admin.verify";

    /** {@code /eco checkpoint}: принудительный снимок. */
    public static final String ADMIN_CHECKPOINT = "codeeconomy.admin.checkpoint";

    /** {@code /eco compact}: свежий чекпоинт и обрезка журнала. */
    public static final String ADMIN_COMPACT = "codeeconomy.admin.compact";

    /** {@code /eco import}: перенос балансов из чужого формата. */
    public static final String ADMIN_IMPORT = "codeeconomy.admin.import";

    /** {@code /eco unlock}: снятие карантина носителя. */
    public static final String ADMIN_UNLOCK = "codeeconomy.admin.unlock";

    /** Обход пола баланса: уход до {@code negativeFloor} разрешён. */
    public static final String BYPASS_MIN_BALANCE = "codeeconomy.bypass.minbalance";

    /** Стартовый баланс группы в мажорных единицах вместо {@code startBalance} валюты. */
    public static final String META_STARTING = "codeeconomy.starting";

    /** Личный потолок одного перевода вместо {@code limits.maxTransfer}. */
    public static final String META_PAY_LIMIT = "codeeconomy.paylimit";

    private EconomyNodes() {}
}
