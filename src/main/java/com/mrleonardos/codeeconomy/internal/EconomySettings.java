package com.mrleonardos.codeeconomy.internal;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codeeconomy.api.CurrencyIds;
import com.mrleonardos.codeeconomy.api.EconomyLimits;
import com.mrleonardos.codeeconomy.internal.store.SchemaMigrations;

public final class EconomySettings {

    public static final String MODID = "codeeconomy";
    public static final String SETTINGS_FILE = "config";
    public static final String CURRENCIES_FILE = "currencies";
    public static final String ACCOUNTS_FILE = "accounts";

    public static final String DEFAULT_PROVIDER = "json";
    public static final String DEFAULT_ON_CORRUPT = "readonly";

    public static final int DEFAULT_AUTOSAVE_SECONDS = 120;
    public static final long DEFAULT_MIN_TRANSFER = 1L;
    public static final long DEFAULT_MAX_TRANSFER = 1000000000L;
    public static final int DEFAULT_PAY_COOLDOWN_SECONDS = 0;
    public static final int DEFAULT_HISTORY_ENTRIES = 10000;
    public static final int DEFAULT_HISTORY_AGE_HOURS = 168;
    public static final int DEFAULT_IDEMPOTENCY_HOURS = 72;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int DEFAULT_CACHE_TICKS = 100;

    private static final EconomySettings DEFAULTS = new EconomySettings();

    public Storage storage = new Storage();
    public String defaultCurrency = CurrencyIds.DEFAULT;
    public Limits limits = new Limits();
    public History history = new History();
    public Top top = new Top();
    public Guards guards = new Guards();
    public Audit audit = new Audit();

    public static EconomySettings defaults() {
        return DEFAULTS;
    }

    public static ConfigSpec<EconomySettings> spec() {
        return ConfigSpec.of(MODID, SETTINGS_FILE, EconomySettings.class)
            .scope(ConfigScope.SETTINGS)
            .schemaVersion(SchemaMigrations.SETTINGS_VERSION)
            .defaults(EconomySettings::defaults)
            .build();
    }

    public EconomyLimits ceilings() {
        return EconomyLimits.builder()
            .historyEntries(history.maxEntries)
            .build();
    }

    /** Потолки с разбором замечаний: кривое значение конфига трактуется как заводское с записью в лог. */
    public EconomyLimits ceilings(Logger log) {
        EconomyLimits.Builder builder = EconomyLimits.builder()
            .historyEntries(history.maxEntries);
        EconomyLimits limits = builder.build();
        for (String remark : builder.remarks()) {
            log.warn("Config ceiling is unusable: {}", remark);
        }
        return limits;
    }

    public String provider() {
        return storage.provider == null || storage.provider.trim()
            .isEmpty() ? DEFAULT_PROVIDER : storage.provider.trim();
    }

    public String currencyId() {
        String normalized = CurrencyIds.normalize(defaultCurrency);
        return CurrencyIds.isValid(normalized) ? normalized : CurrencyIds.DEFAULT;
    }

    public int autosaveTicks() {
        return Math.max(1, storage.autosaveSeconds) * 20;
    }

    public long idempotencyMillis() {
        return Math.max(0, history.idempotencyHours) * 3600L * 1000L;
    }

    public long historyMillis() {
        return Math.max(0, history.maxAgeHours) * 3600L * 1000L;
    }

    public static final class Storage {

        public String provider = DEFAULT_PROVIDER;
        public int autosaveSeconds = DEFAULT_AUTOSAVE_SECONDS;
        public String onCorrupt = DEFAULT_ON_CORRUPT;
    }

    public static final class Limits {

        public long minTransfer = DEFAULT_MIN_TRANSFER;
        public long maxTransfer = DEFAULT_MAX_TRANSFER;
        public int payCooldownSeconds = DEFAULT_PAY_COOLDOWN_SECONDS;
    }

    public static final class History {

        public int maxEntries = DEFAULT_HISTORY_ENTRIES;
        public int maxAgeHours = DEFAULT_HISTORY_AGE_HOURS;
        public int idempotencyHours = DEFAULT_IDEMPOTENCY_HOURS;
    }

    public static final class Top {

        public int pageSize = DEFAULT_PAGE_SIZE;
        public int cacheTicks = DEFAULT_CACHE_TICKS;
    }

    public static final class Guards {

        public boolean failOpen = false;
    }

    public static final class Audit {

        public boolean logChanges = true;
        public boolean logChecks = false;
    }
}
