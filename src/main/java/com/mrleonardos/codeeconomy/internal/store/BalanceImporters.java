package com.mrleonardos.codeeconomy.internal.store;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mrleonardos.codeeconomy.api.Amounts;
import com.mrleonardos.codeeconomy.api.EconomyLimits;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;

/**
 * Читатели переносимых балансов: файл плоской карты uuid в минорные единицы и userdata-каталог
 * Essentials, где баланс лежит в мажорных единицах.
 *
 * <p>
 * Источник никогда не меняется. Каждая строка отчёта объясняет, почему значение не поехало:
 * отрицательное, сверх потолка валюты или с непригодным uuid.
 */
public final class BalanceImporters {

    public static final String FLAT_JSON = "flatjson";

    public static final String ESSENTIALS = "essentials";

    private static final String MONEY_KEY = "money";

    private BalanceImporters() {}

    /** Правда ли формат известен, иначе источник читать нельзя. */
    public static boolean knows(String format) {
        return FLAT_JSON.equals(format) || ESSENTIALS.equals(format);
    }

    /** Прочитать источник и вернуть перенесённые суммы вместе с построчными отказами. */
    public static Imported read(String format, Path source, CurrencyRecord currency, EconomyLimits limits) {
        if (FLAT_JSON.equals(format)) {
            return flatJson(source, currency, limits);
        }
        return essentials(source, currency, limits);
    }

    private static Imported flatJson(Path source, CurrencyRecord currency, EconomyLimits limits) {
        Imported imported = new Imported();
        JsonObject data = readJson(source, imported);
        if (data == null) {
            return imported;
        }
        for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
            UUID player = uuid(entry.getKey(), imported);
            if (player == null) {
                continue;
            }
            if (imported.accepted.size() >= limits.accounts()) {
                imported.rejected.add("the account ceiling of " + limits.accounts() + " is reached");
                return imported;
            }
            Long amount = amount(entry.getValue(), currency, imported, entry.getKey());
            if (amount != null) {
                imported.accepted.put(player, amount);
            }
        }
        return imported;
    }

    private static Imported essentials(Path source, CurrencyRecord currency, EconomyLimits limits) {
        Imported imported = new Imported();
        if (!Files.isDirectory(source)) {
            imported.rejected.add(source + " is not a userdata directory");
            return imported;
        }
        List<Path> files = listUserData(source, imported);
        for (Path file : files) {
            if (imported.accepted.size() >= limits.accounts()) {
                imported.rejected.add("the account ceiling of " + limits.accounts() + " is reached");
                return imported;
            }
            String name = file.getFileName()
                .toString();
            int dot = name.lastIndexOf('.');
            UUID player = uuid(dot <= 0 ? name : name.substring(0, dot), imported);
            if (player == null) {
                continue;
            }
            String money = moneyOf(file, imported);
            Long amount = money == null ? null : amount(money, currency, imported, name);
            if (amount != null) {
                imported.accepted.put(player, amount);
            }
        }
        return imported;
    }

    private static List<Path> listUserData(Path source, Imported imported) {
        try (Stream<Path> files = Files.list(source)) {
            List<Path> found = new ArrayList<>();
            files.forEach(found::add);
            return found;
        } catch (IOException failure) {
            imported.rejected.add(source + " cannot be listed: " + failure.toString());
            return new ArrayList<>();
        }
    }

    private static String moneyOf(Path file, Imported imported) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            for (String line : readLines(reader)) {
                String trimmed = line.trim();
                int colon = trimmed.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, colon)
                    .trim();
                if (!MONEY_KEY.equalsIgnoreCase(key)) {
                    continue;
                }
                return trimmed.substring(colon + 1)
                    .trim();
            }
            imported.rejected.add(file.getFileName() + " carries no money entry");
            return null;
        } catch (IOException failure) {
            imported.rejected.add(file.getFileName() + " cannot be read: " + failure.toString());
            return null;
        }
    }

    private static List<String> readLines(Reader reader) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int symbol = reader.read();
        while (symbol >= 0) {
            if (symbol == '\n') {
                lines.add(line.toString());
                line.setLength(0);
            } else {
                line.append((char) symbol);
            }
            symbol = reader.read();
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static JsonObject readJson(Path source, Imported imported) {
        if (!Files.isRegularFile(source)) {
            imported.rejected.add(source + " is not a file");
            return null;
        }
        try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            JsonElement parsed = new JsonParser().parse(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                imported.rejected.add(source.getFileName() + " is not a json object");
                return null;
            }
            return parsed.getAsJsonObject();
        } catch (IOException | RuntimeException failure) {
            imported.rejected.add(source.getFileName() + " cannot be read: " + failure.toString());
            return null;
        }
    }

    private static UUID uuid(String raw, Imported imported) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException malformed) {
            imported.rejected.add(raw + " is not a uuid");
            return null;
        }
    }

    private static Long amount(JsonElement raw, CurrencyRecord currency, Imported imported, String owner) {
        if (raw == null || !raw.isJsonPrimitive()) {
            imported.rejected.add(owner + " carries no numeric balance");
            return null;
        }
        return amount(
            raw.getAsString()
                .trim(),
            currency,
            imported,
            owner);
    }

    private static Long amount(String raw, CurrencyRecord currency, Imported imported, String owner) {
        long amount;
        try {
            amount = Amounts.parse(raw, currency);
        } catch (RuntimeException malformed) {
            imported.rejected.add(owner + " carries an unusable balance " + raw);
            return null;
        }
        if (amount < currency.minBalance()) {
            imported.rejected.add(owner + " carries " + amount + " below the floor of " + currency.minBalance());
            return null;
        }
        if (amount > currency.maxBalance()) {
            imported.rejected.add(owner + " carries " + amount + " above the ceiling of " + currency.maxBalance());
            return null;
        }
        return Long.valueOf(amount);
    }

    /** Итог чтения источника: перенесённые суммы и объяснённые отказы. */
    public static final class Imported {

        private final Map<UUID, Long> accepted = new LinkedHashMap<>();
        private final List<String> rejected = new ArrayList<>();

        public Map<UUID, Long> accepted() {
            return accepted;
        }

        public List<String> rejected() {
            return rejected;
        }
    }
}
