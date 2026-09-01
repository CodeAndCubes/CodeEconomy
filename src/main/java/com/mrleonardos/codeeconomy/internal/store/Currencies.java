package com.mrleonardos.codeeconomy.internal.store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codecore.api.config.Migration;
import com.mrleonardos.codeeconomy.api.CurrencyIds;
import com.mrleonardos.codeeconomy.api.EconomyLimits;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.internal.EconomyConstants;

/**
 * Валюты из {@code config/code/economy/economy-currencies.toml}.
 *
 * <p>
 * Порядок секций файла доезжает до мода как есть и становится порядком показа. Так работает разбор toml
 * начиная с night-config 3.8: до неё вложенные таблицы заводились на неупорядоченной карте, и порядок
 * терялся. За сохранностью порядка следит {@code CurrenciesTest}.
 */
public final class Currencies {

    private Currencies() {}

    public static ConfigSpec<CurrenciesFile> spec() {
        ConfigSpec.Builder<CurrenciesFile> builder = ConfigSpec
            .of(EconomyConstants.MODID, EconomyConstants.CURRENCIES_FILE, CurrenciesFile.class)
            .role(ConfigRoles.ECONOMY)
            .scope(ConfigScope.SETTINGS)
            .schemaVersion(SchemaMigrations.CURRENCIES_VERSION);
        for (Migration migration : SchemaMigrations.currenciesChain()) {
            builder.migration(migration);
        }
        return builder.defaults(CurrenciesFile::defaults)
            .build();
    }

    /**
     * Разобрать файл. Негодная валюта пропускается с записью в лог, файл без единой годной заменяется
     * заводской {@code coin}.
     */
    public static List<CurrencyRecord> load(CurrenciesFile file, EconomyLimits limits, Logger log) {
        List<CurrencyRecord> loaded = new ArrayList<>();
        if (file != null) {
            for (Map.Entry<String, CurrencyEntry> entry : file.entries()
                .entrySet()) {
                if (loaded.size() >= limits.currencies()) {
                    warn(
                        log,
                        "Currency {} is beyond the ceiling of {} currencies and was skipped",
                        entry.getKey(),
                        Integer.valueOf(limits.currencies()));
                    break;
                }
                CurrencyRecord currency = read(entry.getKey(), entry.getValue(), log);
                if (currency != null) {
                    loaded.add(currency);
                }
            }
        }
        if (loaded.isEmpty()) {
            warn(log, "No usable currency was found, the default {} is restored", CurrencyIds.DEFAULT);
            loaded.add(CurrencyRecord.defaultCoin());
        }
        return loaded;
    }

    /** Валюты по идентификатору: один справочник на движок, команды и хранилище. */
    public static Map<String, CurrencyRecord> byId(List<CurrencyRecord> currencies) {
        Map<String, CurrencyRecord> known = new LinkedHashMap<>();
        for (CurrencyRecord currency : currencies) {
            known.put(currency.id(), currency);
        }
        return known;
    }

    /**
     * Стартовые балансы по валютам: на них опирается счёт, которого журнал ещё не касался.
     *
     * <p>
     * Единственное место, где это правило записано. Разойдись копии в движке и в хранилище, расхождение
     * вылезло бы только на сверке, и объяснить его было бы нечем.
     */
    public static Recovery.StartBalances startBalances(List<CurrencyRecord> currencies) {
        final Map<String, Long> starting = new LinkedHashMap<>();
        for (CurrencyRecord currency : currencies) {
            starting.put(currency.id(), Long.valueOf(currency.startBalance()));
        }
        return new Recovery.StartBalances() {

            @Override
            public long starting(String currencyId) {
                Long value = starting.get(currencyId);
                return value == null ? 0L : value.longValue();
            }
        };
    }

    private static CurrencyRecord read(String key, CurrencyEntry entry, Logger log) {
        String id = CurrencyIds.normalize(key);
        if (!CurrencyIds.isValid(id)) {
            warn(log, "Currency id {} does not match [a-z0-9_]{1,16} and was skipped", key);
            return null;
        }
        if (entry == null || entry.maxBalance == null) {
            warn(
                log,
                "Currency {} carries no maxBalance and was skipped: with a zero ceiling every "
                    + "operation on it would be refused",
                id);
            return null;
        }
        try {
            long maxBalance = clampMaxBalance(entry.maxBalance.longValue(), id, log);
            return CurrencyRecord.builder(id)
                .displayName(entry.displayName)
                .symbol(entry.symbol)
                .decimals(integer(entry.decimals, 0))
                .startBalance(number(entry.startBalance, 0L))
                .minBalance(number(entry.minBalance, 0L))
                .negativeFloor(number(entry.negativeFloor, 0L))
                .maxBalance(maxBalance)
                .payAllowed(flag(entry.payAllowed, true))
                .visible(flag(entry.visible, true))
                .format(entry.format)
                .build();
        } catch (RuntimeException failure) {
            warn(log, "Currency {} is unusable and was skipped: {}", id, failure.getMessage());
            return null;
        }
    }

    private static long clampMaxBalance(long requested, String id, Logger log) {
        long capped = EconomyLimits.defaults()
            .capMaxBalance(requested);
        if (capped != requested) {
            warn(
                log,
                "Currency {} declares maxBalance {} above the ceiling of {}, the value is capped",
                id,
                Long.valueOf(requested),
                Long.valueOf(capped));
        }
        return capped;
    }

    private static int integer(Integer value, int fallback) {
        return value == null ? fallback : value.intValue();
    }

    private static long number(Long value, long fallback) {
        return value == null ? fallback : value.longValue();
    }

    private static boolean flag(Boolean value, boolean fallback) {
        return value == null ? fallback : value.booleanValue();
    }

    private static void warn(Logger log, String message, Object... arguments) {
        if (log != null) {
            log.warn(message, arguments);
        }
    }
}
