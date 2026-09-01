package com.mrleonardos.codeeconomy.api.model;

import java.util.Objects;

import com.mrleonardos.codeeconomy.api.CurrencyIds;
import com.mrleonardos.codeeconomy.api.EconomyLimits;

/**
 * Валюта: идентификатор, показ и правила денег.
 *
 * <p>
 * Живёт в {@code config/code/economy/economy-currencies.toml}, правится человеком. Отсюда команды берут, как
 * разобрать введённую сумму и как её напечатать, а конвейер перевода берёт пол, потолок и разрешение
 * на переводы между игроками. Суммы везде в минорных единицах, {@code decimals} говорит лишь о том,
 * сколько знаков из них показываются человеку.
 */
public final class CurrencyRecord {

    /** Подстановка суммы в шаблоне показа. */
    public static final String AMOUNT_PLACEHOLDER = "%amount%";

    /** Подстановка знака валюты в шаблоне показа. */
    public static final String SYMBOL_PLACEHOLDER = "%symbol%";

    /** Заводской шаблон показа суммы. */
    public static final String DEFAULT_FORMAT = AMOUNT_PLACEHOLDER + " " + SYMBOL_PLACEHOLDER;

    private final String id;
    private final String displayName;
    private final String symbol;
    private final int decimals;
    private final long startBalance;
    private final long minBalance;
    private final long negativeFloor;
    private final long maxBalance;
    private final boolean payAllowed;
    private final boolean visible;
    private final String format;

    private CurrencyRecord(String id, String displayName, String symbol, int decimals, long startBalance,
        long minBalance, long negativeFloor, long maxBalance, boolean payAllowed, boolean visible, String format) {
        this.id = id;
        this.displayName = displayName;
        this.symbol = symbol;
        this.decimals = decimals;
        this.startBalance = startBalance;
        this.minBalance = minBalance;
        this.negativeFloor = negativeFloor;
        this.maxBalance = maxBalance;
        this.payAllowed = payAllowed;
        this.visible = visible;
        this.format = format;
    }

    /** Начать собирать валюту. */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** Идентификатор, нижний регистр, {@code [a-z0-9_]{1,16}}. */
    public String id() {
        return id;
    }

    /** Название для человека. */
    public String displayName() {
        return displayName;
    }

    /** Знак валюты для показа, например {@code $.} */
    public String symbol() {
        return symbol;
    }

    /** Сколько младших знаков видно человеку, от 0 до 4. */
    public int decimals() {
        return decimals;
    }

    /** Баланс нового счёта в минорных единицах. */
    public long startBalance() {
        return startBalance;
    }

    /** Пол баланса: ниже него обычная операция не уводит. */
    public long minBalance() {
        return minBalance;
    }

    /** Дно для операций с обходом пола, обычно ноль или отрицательное. */
    public long negativeFloor() {
        return negativeFloor;
    }

    /** Потолок баланса в минорных единицах, не выше {@code EconomyLimits.MAX_BALANCE_CAP}. */
    public long maxBalance() {
        return maxBalance;
    }

    /** Правда ли переводы между игроками разрешены. */
    public boolean payAllowed() {
        return payAllowed;
    }

    /** Правда ли валюта видна игроку в командах. */
    public boolean visible() {
        return visible;
    }

    /** Шаблон показа, подставляет {@code %amount%} и {@code %symbol%}. */
    public String format() {
        return format;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CurrencyRecord)) {
            return false;
        }
        CurrencyRecord that = (CurrencyRecord) other;
        return decimals == that.decimals && startBalance == that.startBalance
            && minBalance == that.minBalance
            && negativeFloor == that.negativeFloor
            && maxBalance == that.maxBalance
            && payAllowed == that.payAllowed
            && visible == that.visible
            && id.equals(that.id)
            && displayName.equals(that.displayName)
            && symbol.equals(that.symbol)
            && format.equals(that.format);
    }

    @Override
    public int hashCode() {
        return (((((((((id.hashCode() * 31 + displayName.hashCode()) * 31 + symbol.hashCode()) * 31 + decimals) * 31
            + Long.hashCode(startBalance)) * 31 + Long.hashCode(minBalance)) * 31 + Long.hashCode(negativeFloor)) * 31
            + Long.hashCode(maxBalance)) * 31 + Boolean.hashCode(payAllowed)) * 31 + Boolean.hashCode(visible)) * 31
            + format.hashCode();
    }

    @Override
    public String toString() {
        return id + " " + format;
    }

    /** Валюта из заводского файла: одна {@code coin} с двумя знаками и потолком в триллион. */
    public static CurrencyRecord defaultCoin() {
        return builder(CurrencyIds.DEFAULT).displayName("Coins")
            .symbol("$")
            .decimals(2)
            .startBalance(25000L)
            .minBalance(0L)
            .negativeFloor(0L)
            .maxBalance(1000000000000L)
            .payAllowed(true)
            .visible(true)
            .format(DEFAULT_FORMAT)
            .build();
    }

    /** Сборщик валюты из значений файла. */
    public static final class Builder {

        private final String id;
        private String displayName;
        private String symbol;
        private int decimals;
        private long startBalance;
        private long minBalance;
        private long negativeFloor;
        private long maxBalance;
        private boolean maxBalanceSet;
        private boolean payAllowed = true;
        private boolean visible = true;
        private String format = DEFAULT_FORMAT;

        private Builder(String id) {
            this.id = CurrencyIds.checked(id);
        }

        /** Название для человека, пустое заменяется идентификатором. */
        public Builder displayName(String value) {
            displayName = value;
            return this;
        }

        /** Знак валюты, пустой заменяется идентификатором. */
        public Builder symbol(String value) {
            symbol = value;
            return this;
        }

        /** Число знаков после разделителя, от 0 до 4. */
        public Builder decimals(int value) {
            decimals = value;
            return this;
        }

        /** Стартовый баланс в минорных единицах. */
        public Builder startBalance(long value) {
            startBalance = value;
            return this;
        }

        /** Пол баланса. */
        public Builder minBalance(long value) {
            minBalance = value;
            return this;
        }

        /** Дно для операций с обходом пола. */
        public Builder negativeFloor(long value) {
            negativeFloor = value;
            return this;
        }

        /** Потолок баланса, значение выше заводского капа отклоняется. */
        public Builder maxBalance(long value) {
            maxBalance = value;
            maxBalanceSet = true;
            return this;
        }

        /** Разрешить или закрыть переводы между игроками. */
        public Builder payAllowed(boolean value) {
            payAllowed = value;
            return this;
        }

        /** Показывать ли валюту игроку. */
        public Builder visible(boolean value) {
            visible = value;
            return this;
        }

        /** Шаблон показа, обязан нести {@code %amount%}, пустой заменяется заводским. */
        public Builder format(String value) {
            if (value != null) {
                format = value;
            }
            return this;
        }

        /**
         * Готовая валюта.
         *
         * @throws IllegalArgumentException если нарушены границы полей или пол оказался выше потолка
         */
        public CurrencyRecord build() {
            if (decimals < EconomyLimits.MIN_DECIMALS || decimals > EconomyLimits.MAX_DECIMALS) {
                throw new IllegalArgumentException(
                    "Decimals must be between " + EconomyLimits.MIN_DECIMALS
                        + " and "
                        + EconomyLimits.MAX_DECIMALS
                        + ": "
                        + decimals);
            }
            if (!maxBalanceSet) {
                throw new IllegalArgumentException("Currency " + id + " needs maxBalance");
            }
            if (maxBalance < 0 || maxBalance > EconomyLimits.MAX_BALANCE_CAP) {
                throw new IllegalArgumentException(
                    "maxBalance must be between 0 and " + EconomyLimits.MAX_BALANCE_CAP + ": " + maxBalance);
            }
            if (negativeFloor > 0) {
                throw new IllegalArgumentException("negativeFloor must not be above zero: " + negativeFloor);
            }
            if (negativeFloor > minBalance) {
                throw new IllegalArgumentException("negativeFloor must not be above minBalance: " + negativeFloor);
            }
            if (minBalance > maxBalance) {
                throw new IllegalArgumentException("minBalance must not be above maxBalance: " + minBalance);
            }
            if (startBalance < minBalance || startBalance > maxBalance) {
                throw new IllegalArgumentException(
                    "startBalance must be between minBalance and maxBalance: " + startBalance);
            }
            String shownName = nonEmpty(displayName, id);
            String shownSymbol = nonEmpty(symbol, id);
            String checkedFormat = Objects.requireNonNull(format, "format")
                .trim();
            if (checkedFormat.isEmpty() || !checkedFormat.contains(AMOUNT_PLACEHOLDER)) {
                throw new IllegalArgumentException("Format must carry " + AMOUNT_PLACEHOLDER + ": " + format);
            }
            return new CurrencyRecord(
                id,
                shownName,
                shownSymbol,
                decimals,
                startBalance,
                minBalance,
                negativeFloor,
                maxBalance,
                payAllowed,
                visible,
                checkedFormat);
        }

        private static String nonEmpty(String value, String fallback) {
            if (value == null) {
                return fallback;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? fallback : trimmed;
        }
    }
}
