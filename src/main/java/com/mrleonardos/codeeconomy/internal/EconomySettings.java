package com.mrleonardos.codeeconomy.internal;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.config.Comment;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codecore.api.config.Migration;
import com.mrleonardos.codeeconomy.api.EconomyLimits;
import com.mrleonardos.codeeconomy.internal.store.SchemaMigrations;

/**
 * Содержимое {@code config/code/economy/economy.toml}.
 *
 * <p>
 * Здесь остаётся редкое: сроки и объём истории, страница и кэш топа, поведение цепочки гвардов при сбое.
 * Хранилище, журнал в логе, валюта по умолчанию и границы перевода уехали в главный файл линейки, и
 * второго места с этими ключами нет.
 */
public final class EconomySettings {

    public static final int DEFAULT_HISTORY_ENTRIES = 10000;
    public static final int DEFAULT_HISTORY_AGE_HOURS = 168;
    public static final int DEFAULT_IDEMPOTENCY_HOURS = 72;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int DEFAULT_CACHE_TICKS = 100;

    /** Потолок страницы: чат всё равно столько не покажет, а произведение страницы на размер растёт. */
    public static final int MAX_PAGE_SIZE = 100;

    private static final EconomySettings DEFAULTS = new EconomySettings();

    public History history = new History();
    public Top top = new Top();
    public Guards guards = new Guards();

    public static EconomySettings defaults() {
        return DEFAULTS;
    }

    public static ConfigSpec<EconomySettings> spec() {
        ConfigSpec.Builder<EconomySettings> builder = ConfigSpec.settings(EconomyConstants.MODID, EconomySettings.class)
            .role(ConfigRoles.ECONOMY)
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

    /** Размер страницы топа и истории, зажатый потолком: страница на размер считается в long. */
    public int pageSize() {
        return Math.max(1, Math.min(top.pageSize, MAX_PAGE_SIZE));
    }

    public long idempotencyMillis() {
        return Math.max(0, history.idempotencyHours) * 3600L * 1000L;
    }

    public long historyMillis() {
        return Math.max(0, history.maxAgeHours) * 3600L * 1000L;
    }

    @Comment("История операций, которую мод держит в памяти для /balance history и повторов.")
    public static final class History {

        @Comment("Сколько записей держать. Ниже этого числа старые записи выбрасываются.")
        public int maxEntries = DEFAULT_HISTORY_ENTRIES;

        @Comment("Сколько часов записи живут. Заводские 168 это неделя.")
        public int maxAgeHours = DEFAULT_HISTORY_AGE_HOURS;

        @Comment({ "Сколько часов повтор операции с тем же идентификатором отвечает записанным исходом.",
            "Меньше этого срока деньги не двинутся дважды, больше срока повтор проведут заново." })
        public int idempotencyHours = DEFAULT_IDEMPOTENCY_HOURS;
    }

    @Comment("Топ балансов: /baltop.")
    public static final class Top {

        @Comment("Сколько строк на странице. Выше 100 не поднять.")
        public int pageSize = DEFAULT_PAGE_SIZE;

        @Comment("Сколько тиков готовый топ считается свежим. 100 тиков это пять секунд.")
        public int cacheTicks = DEFAULT_CACHE_TICKS;
    }

    @Comment("Гварды: проверки чужих модов, через которые проходит каждый перевод.")
    public static final class Guards {

        @Comment({ "Что делать, когда гвард упал с ошибкой.", "false отклоняет перевод, true пропускает его дальше." })
        public boolean failOpen = false;
    }
}
