package com.mrleonardos.codeeconomy.api;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Правила идентификатора валюты и заводское значение.
 *
 * <p>
 * Идентификатор живёт в конфиге, в командах и в журнале, поэтому он короткий, нижним регистром и без
 * знаков, за которые цепляется разбор. Единая проверка здесь, чтобы команды, файл валют и чужие моды
 * не спорили о том, что считается корректным.
 */
public final class CurrencyIds {

    /** Валюта из заводского файла, она же значение настройки {@code defaultCurrency}. */
    public static final String DEFAULT = "coin";

    private static final Pattern PATTERN = Pattern.compile("[a-z0-9_]{1,16}");

    private CurrencyIds() {}

    /** Правда ли идентификатор вписывается в правило {@code [a-z0-9_]{1,16}}. */
    public static boolean isValid(String id) {
        return id != null && PATTERN.matcher(id)
            .matches();
    }

    /** Привести введённое к каноничному виду: обрезать пробелы и опустить регистр. */
    public static String normalize(String id) {
        if (id == null) {
            return null;
        }
        return id.trim()
            .toLowerCase(Locale.ROOT);
    }

    /** Привести и проверить: каноничный идентификатор или отказ с пояснением. */
    public static String checked(String id) {
        String normalized = normalize(id);
        if (!isValid(normalized)) {
            throw new IllegalArgumentException("Currency id must match [a-z0-9_]{1,16}: " + id);
        }
        return normalized;
    }
}
