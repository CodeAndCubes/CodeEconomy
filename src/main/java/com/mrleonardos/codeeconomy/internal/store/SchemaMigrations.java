package com.mrleonardos.codeeconomy.internal.store;

import java.util.Collections;
import java.util.List;

import com.mrleonardos.codecore.api.config.Migration;

/**
 * Версии схем и цепочки миграций файлов мода.
 *
 * <p>
 * Цепочки пустые, и это единственное место, где они появятся. Каждая цепочка подключается к своему
 * {@code ConfigSpec} сразу, даже пустой: иначе первая написанная миграция легла бы сюда и тихо не
 * применилась.
 */
public final class SchemaMigrations {

    public static final int SETTINGS_VERSION = 1;
    public static final int CURRENCIES_VERSION = 1;
    public static final int ACCOUNTS_VERSION = 1;

    private static final List<Migration> SETTINGS_CHAIN = Collections.emptyList();
    private static final List<Migration> CURRENCIES_CHAIN = Collections.emptyList();
    private static final List<Migration> ACCOUNTS_CHAIN = Collections.emptyList();

    private SchemaMigrations() {}

    public static List<Migration> settingsChain() {
        return SETTINGS_CHAIN;
    }

    public static List<Migration> currenciesChain() {
        return CURRENCIES_CHAIN;
    }

    public static List<Migration> accountsChain() {
        return ACCOUNTS_CHAIN;
    }
}
