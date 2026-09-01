package com.mrleonardos.codeeconomy.api;

import java.util.Objects;

import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;

/**
 * Разбор и печать сумм в минорных единицах.
 *
 * <p>
 * Разбор идёт по {@code decimals} валюты: {@code 12.50} в валюте с двумя знаками даёт 1250. Лишний
 * знак после разделителя отклоняется, молчаливого округления нет. Разделителем служат точка и
 * запятая, ведущий минус разбирается, всё прочее, включая пробелы внутри, отклоняется. Значение за
 * пределами {@code long} отклоняется, а не заворачивается через переполнение.
 *
 * <p>
 * Печать идёт по шаблону валюты: {@code %amount%} заменяется суммой с точкой и нулями добитыми до
 * нужного числа знаков, {@code %symbol%} знаком валюты.
 */
public final class Amounts {

    private static final char DOT = '.';

    private static final char COMMA = ',';

    private static final char MINUS = '-';

    private static final long[] SCALE = { 1L, 10L, 100L, 1000L, 10000L };

    private Amounts() {}

    /**
     * Разобрать введённую сумму в минорные единицы.
     *
     * @throws IllegalArgumentException если сумма пустая, несёт посторонний символ, несёт больше знаков
     *                                  после разделителя, чем допускает валюта, или не влезает в {@code long}
     */
    public static long parse(String input, CurrencyRecord currency) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(currency, "currency");
        String text = input.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Amount is empty");
        }
        boolean negative = false;
        int index = 0;
        if (text.charAt(0) == MINUS) {
            negative = true;
            index = 1;
            if (text.length() == 1) {
                throw new IllegalArgumentException("Amount carries no digits: " + input);
            }
        }
        long major = 0L;
        long fraction = 0L;
        int fractionDigits = 0;
        boolean separatorSeen = false;
        for (; index < text.length(); index++) {
            char symbol = text.charAt(index);
            if (symbol == DOT || symbol == COMMA) {
                if (separatorSeen) {
                    throw new IllegalArgumentException("Amount carries two separators: " + input);
                }
                separatorSeen = true;
                continue;
            }
            if (symbol < '0' || symbol > '9') {
                throw new IllegalArgumentException("Amount carries a stranger symbol: " + input);
            }
            int digit = symbol - '0';
            if (!separatorSeen) {
                if (major > (Long.MAX_VALUE - digit) / 10L) {
                    throw new IllegalArgumentException("Amount does not fit into long: " + input);
                }
                major = major * 10L + digit;
            } else {
                if (fractionDigits == currency.decimals()) {
                    throw new IllegalArgumentException("Amount carries more digits than the currency allows: " + input);
                }
                fraction = fraction * 10L + digit;
                fractionDigits++;
            }
        }
        if (separatorSeen && fractionDigits == 0) {
            throw new IllegalArgumentException("Amount carries a separator without digits: " + input);
        }
        long scale = SCALE[currency.decimals()];
        if (major > Long.MAX_VALUE / scale) {
            throw new IllegalArgumentException("Amount does not fit into long: " + input);
        }
        long scaledMajor = major * scale;
        long scaledFraction = fraction * SCALE[currency.decimals() - fractionDigits];
        if (scaledMajor > Long.MAX_VALUE - scaledFraction) {
            throw new IllegalArgumentException("Amount does not fit into long: " + input);
        }
        long amount = scaledMajor + scaledFraction;
        return negative ? -amount : amount;
    }

    /**
     * Перевести мажорные единицы, как их хранят внешние системы, в минорные.
     *
     * @throws IllegalArgumentException если результат не влезает в {@code long}
     */
    public static long fromMajor(long major, CurrencyRecord currency) {
        Objects.requireNonNull(currency, "currency");
        long scale = SCALE[currency.decimals()];
        if (major > Long.MAX_VALUE / scale || major < Long.MIN_VALUE / scale) {
            throw new IllegalArgumentException("Amount does not fit into long: " + major);
        }
        return major * scale;
    }

    /** Перевести минорные единицы в мажорные, остаток отбрасывается. */
    public static long toMajor(long minor, CurrencyRecord currency) {
        Objects.requireNonNull(currency, "currency");
        return minor / SCALE[currency.decimals()];
    }

    /** Сумма по шаблону валюты. */
    public static String format(long amount, CurrencyRecord currency) {
        Objects.requireNonNull(currency, "currency");
        return currency.format()
            .replace(CurrencyRecord.AMOUNT_PLACEHOLDER, formatAmount(amount, currency.decimals()))
            .replace(CurrencyRecord.SYMBOL_PLACEHOLDER, currency.symbol());
    }

    /** Сумма без шаблона: целая часть, точка и дробная часть добитая нулями до {@code decimals}. */
    public static String formatAmount(long amount, int decimals) {
        if (decimals < EconomyLimits.MIN_DECIMALS || decimals > EconomyLimits.MAX_DECIMALS) {
            throw new IllegalArgumentException(
                "Decimals must be between " + EconomyLimits.MIN_DECIMALS
                    + " and "
                    + EconomyLimits.MAX_DECIMALS
                    + ": "
                    + decimals);
        }
        long scale = SCALE[decimals];
        long major = amount / scale;
        long fraction = Math.abs(amount % scale);
        StringBuilder text = new StringBuilder();
        if (major == 0L && amount < 0L) {
            text.append(MINUS);
        }
        text.append(major);
        if (decimals > 0) {
            text.append(DOT);
            String digits = Long.toString(fraction);
            for (int pad = digits.length(); pad < decimals; pad++) {
                text.append('0');
            }
            text.append(digits);
        }
        return text.toString();
    }
}
