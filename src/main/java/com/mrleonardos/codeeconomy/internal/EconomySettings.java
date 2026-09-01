package com.mrleonardos.codeeconomy.internal;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codecore.api.config.Migration;
import com.mrleonardos.codecore.api.service.ServicePriority;
import com.mrleonardos.codeeconomy.api.CurrencyIds;
import com.mrleonardos.codeeconomy.api.EconomyLimits;
import com.mrleonardos.codeeconomy.internal.store.SchemaMigrations;

public final class EconomySettings {

    public static final String MODID = "codeeconomy";
    public static final String SETTINGS_FILE = "config";
    public static final String CURRENCIES_FILE = "currencies";
    public static final String ACCOUNTS_FILE = "accounts";

    public static final String DEFAULT_PROVIDER = "json";

    /**
     * Вес реализации в реестре ядра. Роль у мода такая же, как у CodePerms: отдельный мод поверх
     * встроенной заглушки ядра, поэтому вес по всей линейке один и это {@code ADDON}. Потолок
     * {@code OVERRIDE} остаётся администратору на случай, когда иначе не разрулить.
     */
    public static final ServicePriority DEFAULT_PRIORITY = ServicePriority.ADDON;

    public static final int DEFAULT_AUTOSAVE_SECONDS = 120;
    public static final long DEFAULT_MIN_TRANSFER = 1L;
    public static final long DEFAULT_MAX_TRANSFER = 1000000000L;
    public static final int DEFAULT_PAY_COOLDOWN_SECONDS = 0;
    public static final int DEFAULT_HISTORY_ENTRIES = 10000;
    public static final int DEFAULT_HISTORY_AGE_HOURS = 168;
    public static final int DEFAULT_IDEMPOTENCY_HOURS = 72;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int DEFAULT_CACHE_TICKS = 100;

    /** Потолок страницы: чат всё равно столько не покажет, а произведение страницы на размер растёт. */
    public static final int MAX_PAGE_SIZE = 100;

    private static final EconomySettings DEFAULTS = new EconomySettings();

    public Storage storage = new Storage();
    public String defaultCurrency = CurrencyIds.DEFAULT;
    public Limits limits = new Limits();
    public History history = new History();
    public Top top = new Top();
    public Guards guards = new Guards();
    public Audit audit = new Audit();

    public Service service = new Service();

    public static EconomySettings defaults() {
        return DEFAULTS;
    }

    public static ConfigSpec<EconomySettings> spec() {
        ConfigSpec.Builder<EconomySettings> builder = ConfigSpec.of(MODID, SETTINGS_FILE, EconomySettings.class)
            .scope(ConfigScope.SETTINGS)
            .schemaVersion(SchemaMigrations.SETTINGS_VERSION);
        for (Migration migration : SchemaMigrations.settingsChain()) {
            builder.migration(migration);
        }
        return builder.defaults(EconomySettings::defaults)
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

    /**
     * Вес, с которым мод предлагает {@code EconomyService} реестру ядра. Незнакомое имя трактуется как
     * отсутствие настройки: молча уронить экономику из-за опечатки в конфиге хуже, чем встать заводским
     * весом и объяснить это в логе.
     */
    public ServicePriority priority(Logger log) {
        String requested = service.priority == null ? null : service.priority.trim();
        if (requested == null || requested.isEmpty()) {
            return DEFAULT_PRIORITY;
        }
        for (ServicePriority known : ServicePriority.values()) {
            if (known.name()
                .equalsIgnoreCase(requested)) {
                return known;
            }
        }
        log.warn("Unknown service.priority value {}, the factory {} is used", requested, DEFAULT_PRIORITY);
        return DEFAULT_PRIORITY;
    }

    /** Размер страницы топа и истории, зажатый потолком: страница на размер считается в long. */
    public int pageSize() {
        return Math.max(1, Math.min(top.pageSize, MAX_PAGE_SIZE));
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
    }

    public static final class Service {

        public String priority = DEFAULT_PRIORITY.name();
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
