package com.mrleonardos.codeeconomy.internal.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codecore.api.config.Migration;
import com.mrleonardos.codeeconomy.api.CurrencyIds;
import com.mrleonardos.codeeconomy.api.EconomyLimits;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.internal.EconomySettings;

public final class Currencies {

    public static final String LIST_FIELD = "currencies";

    public static final String ID = "id";
    public static final String DISPLAY_NAME = "displayName";
    public static final String SYMBOL = "symbol";
    public static final String DECIMALS = "decimals";
    public static final String START_BALANCE = "startBalance";
    public static final String MIN_BALANCE = "minBalance";
    public static final String NEGATIVE_FLOOR = "negativeFloor";
    public static final String MAX_BALANCE = "maxBalance";
    public static final String PAY_ALLOWED = "payAllowed";
    public static final String VISIBLE = "visible";
    public static final String FORMAT = "format";

    private Currencies() {}

    public static ConfigSpec<JsonObject> spec() {
        ConfigSpec.Builder<JsonObject> builder = ConfigSpec
            .of(EconomySettings.MODID, EconomySettings.CURRENCIES_FILE, JsonObject.class)
            .scope(ConfigScope.SETTINGS)
            .schemaVersion(SchemaMigrations.CURRENCIES_VERSION);
        for (Migration migration : SchemaMigrations.currenciesChain()) {
            builder.migration(migration);
        }
        return builder.defaults(Currencies::defaults)
            .build();
    }

    public static JsonObject defaults() {
        JsonObject file = new JsonObject();
        file.add(LIST_FIELD, encode(Collections.singletonList(CurrencyRecord.defaultCoin())));
        return file;
    }

    public static List<CurrencyRecord> load(JsonObject file, EconomyLimits limits, Logger log) {
        List<CurrencyRecord> loaded = new ArrayList<>();
        for (JsonElement element : array(file.get(LIST_FIELD))) {
            if (loaded.size() >= limits.currencies()) {
                warn(
                    log,
                    "Currency {} is beyond the ceiling of {} currencies and was skipped",
                    ID,
                    limits.currencies());
                break;
            }
            if (!element.isJsonObject()) {
                warn(log, "Currency entry that is not an object was skipped");
                continue;
            }
            CurrencyRecord currency = read(element.getAsJsonObject(), limits, log);
            if (currency == null) {
                continue;
            }
            loaded.add(currency);
        }
        if (loaded.isEmpty()) {
            warn(log, "No usable currency was found, the default {} is restored", CurrencyIds.DEFAULT);
            loaded.add(CurrencyRecord.defaultCoin());
        }
        return loaded;
    }

    public static Map<String, CurrencyRecord> byId(List<CurrencyRecord> currencies) {
        Map<String, CurrencyRecord> known = new LinkedHashMap<>();
        for (CurrencyRecord currency : currencies) {
            known.put(currency.id(), currency);
        }
        return known;
    }

    public static Map<String, Long> startingBalances(List<CurrencyRecord> currencies) {
        Map<String, Long> starting = new LinkedHashMap<>();
        for (CurrencyRecord currency : currencies) {
            starting.put(currency.id(), Long.valueOf(currency.startBalance()));
        }
        return starting;
    }

    private static CurrencyRecord read(JsonObject data, EconomyLimits limits, Logger log) {
        String id = CurrencyIds.normalize(text(data.get(ID)));
        if (!CurrencyIds.isValid(id)) {
            warn(log, "Currency id {} does not match [a-z0-9_]{1,16} and was skipped", text(data.get(ID)));
            return null;
        }
        try {
            long maxBalance = clampMaxBalance(longOf(data.get(MAX_BALANCE), 0L), id, log);
            return CurrencyRecord.builder(id)
                .displayName(text(data.get(DISPLAY_NAME)))
                .symbol(text(data.get(SYMBOL)))
                .decimals(integer(data.get(DECIMALS), 0))
                .startBalance(longOf(data.get(START_BALANCE), 0L))
                .minBalance(longOf(data.get(MIN_BALANCE), 0L))
                .negativeFloor(longOf(data.get(NEGATIVE_FLOOR), 0L))
                .maxBalance(maxBalance)
                .payAllowed(booleanOf(data.get(PAY_ALLOWED), true))
                .visible(booleanOf(data.get(VISIBLE), true))
                .format(text(data.get(FORMAT)))
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
                requested,
                capped);
        }
        return capped;
    }

    public static JsonArray encode(List<CurrencyRecord> currencies) {
        JsonArray array = new JsonArray();
        for (CurrencyRecord currency : currencies) {
            JsonObject data = new JsonObject();
            data.addProperty(ID, currency.id());
            data.addProperty(DISPLAY_NAME, currency.displayName());
            data.addProperty(SYMBOL, currency.symbol());
            data.addProperty(DECIMALS, Integer.valueOf(currency.decimals()));
            data.addProperty(START_BALANCE, Long.valueOf(currency.startBalance()));
            data.addProperty(MIN_BALANCE, Long.valueOf(currency.minBalance()));
            data.addProperty(NEGATIVE_FLOOR, Long.valueOf(currency.negativeFloor()));
            data.addProperty(MAX_BALANCE, Long.valueOf(currency.maxBalance()));
            data.addProperty(PAY_ALLOWED, Boolean.valueOf(currency.payAllowed()));
            data.addProperty(VISIBLE, Boolean.valueOf(currency.visible()));
            data.addProperty(FORMAT, currency.format());
            array.add(data);
        }
        return array;
    }

    private static Iterable<JsonElement> array(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return Collections.emptyList();
        }
        return element.getAsJsonArray();
    }

    private static String text(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }

    private static int integer(JsonElement element, int fallback) {
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException malformed) {
            return fallback;
        }
    }

    private static long longOf(JsonElement element, long fallback) {
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsLong();
        } catch (RuntimeException malformed) {
            return fallback;
        }
    }

    private static boolean booleanOf(JsonElement element, boolean fallback) {
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsBoolean();
        } catch (RuntimeException malformed) {
            return fallback;
        }
    }

    private static void warn(Logger log, String message, Object... arguments) {
        if (log != null) {
            log.warn(message, arguments);
        }
    }
}
