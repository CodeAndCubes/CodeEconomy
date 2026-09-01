package com.mrleonardos.codeeconomy.internal;

/** Имена, из которых ядро складывает пути файлов мода. */
public final class EconomyConstants {

    public static final String MODID = "codeeconomy";

    /** Имя владельца в секции {@code [owners]} главного файла. */
    public static final String OWNER = MODID;

    /** Валюты: {@code config/code/economy/economy-currencies.toml}. */
    public static final String CURRENCIES_FILE = "currencies";

    /** Чекпоинт счетов: {@code <мир>/code/economy/economy-accounts.json}. */
    public static final String ACCOUNTS_FILE = "accounts";

    /**
     * Журнал: {@code <мир>/code/economy/economy-journal.jsonl}. Имя целиком, потому что журнал ведёт сам
     * мод и приставку владельца ядро ему не подставляет.
     */
    public static final String JOURNAL_FILE = "economy-journal.jsonl";

    private EconomyConstants() {}
}
