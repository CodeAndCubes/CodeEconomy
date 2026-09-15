package com.mrleonardos.codeeconomy.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Потолки модели: сколько символов, валют, счетов и записей способна унести одна операция.
 *
 * <p>
 * Нужны там, где значение приходит от человека или от чужого мода: команда, вызов API, разбор файла.
 * Без потолка одна команда с длинным хвостом раздувает журнал.
 *
 * <p>
 * Заводские значения это верхняя граница. Через конфиг потолки меняются только вниз: значение выше
 * заводского {@link Builder} зажимает до заводского, отрицательное и бессмысленное заменяет заводским
 * и объясняет это в {@link Builder#remarks()}. Другого способа задать потолки нет, чтобы правило
 * «только вниз» жило в одном месте.
 */
public final class EconomyLimits {

    /** Наибольшая длина {@code transactionId} в символах. */
    public static final int DEFAULT_TRANSACTION_ID_LENGTH = 64;

    /** Наибольшая длина причины операции в символах. */
    public static final int DEFAULT_REASON_LENGTH = 128;

    /** Наибольшее число валют на сервере. */
    public static final int DEFAULT_CURRENCIES = 16;

    /** Наибольшее число счетов в файле состояния. */
    public static final int DEFAULT_ACCOUNTS = 200000;

    /**
     * Наибольшее число записей истории, которое держит движок. Это потолок, а не заводское значение
     * настройки: заводское живёт в {@code EconomySettings.DEFAULT_HISTORY_ENTRIES} и называет другое.
     */
    public static final int HISTORY_ENTRIES_CAP = 10000;

    /** Наименьшее допустимое число знаков после разделителя. */
    public static final int MIN_DECIMALS = 0;

    /** Наибольшее допустимое число знаков после разделителя. */
    public static final int MAX_DECIMALS = 4;

    /** Заводской кап баланса: половина {@code Long.MAX_VALUE}, запас на арифметику без переполнения. */
    public static final long MAX_BALANCE_CAP = 4611686018427387903L;

    private final int transactionIdLength;
    private final int reasonLength;
    private final int currencies;
    private final int accounts;
    private final int historyEntries;

    private EconomyLimits(int transactionIdLength, int reasonLength, int currencies, int accounts, int historyEntries) {
        this.transactionIdLength = transactionIdLength;
        this.reasonLength = reasonLength;
        this.currencies = currencies;
        this.accounts = accounts;
        this.historyEntries = historyEntries;
    }

    /** Заводские потолки. */
    public static EconomyLimits defaults() {
        return new EconomyLimits(
            DEFAULT_TRANSACTION_ID_LENGTH,
            DEFAULT_REASON_LENGTH,
            DEFAULT_CURRENCIES,
            DEFAULT_ACCOUNTS,
            HISTORY_ENTRIES_CAP);
    }

    /** Начать собирать потолки из значений конфига. */
    public static Builder builder() {
        return new Builder();
    }

    /** Наибольшая длина {@code transactionId} в символах. */
    public int transactionIdLength() {
        return transactionIdLength;
    }

    /** Наибольшая длина причины в символах. */
    public int reasonLength() {
        return reasonLength;
    }

    /** Наибольшее число валют. */
    public int currencies() {
        return currencies;
    }

    /** Наибольшее число счетов в файле состояния. */
    public int accounts() {
        return accounts;
    }

    /** Наибольшее число записей истории. */
    public int historyEntries() {
        return historyEntries;
    }

    /** Вписывается ли идентификатор операции в потолок по длине. */
    public boolean acceptsTransactionId(String transactionId) {
        return transactionId != null && !transactionId.isEmpty() && transactionId.length() <= transactionIdLength;
    }

    /** Вписывается ли причина в потолок по длине. */
    public boolean acceptsReason(String reason) {
        return reason != null && reason.length() <= reasonLength;
    }

    /**
     * Баланс, зажатый заводским капом.
     *
     * @param requested значение из конфига или файла
     * @return то же значение, если оно не выше капа, иначе кап
     */
    public long capMaxBalance(long requested) {
        return Math.min(requested, MAX_BALANCE_CAP);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EconomyLimits)) {
            return false;
        }
        EconomyLimits that = (EconomyLimits) other;
        return transactionIdLength == that.transactionIdLength && reasonLength == that.reasonLength
            && currencies == that.currencies
            && accounts == that.accounts
            && historyEntries == that.historyEntries;
    }

    @Override
    public int hashCode() {
        return (((transactionIdLength * 31 + reasonLength) * 31 + currencies) * 31 + accounts) * 31 + historyEntries;
    }

    @Override
    public String toString() {
        return "transactionId " + transactionIdLength
            + "ch, reason "
            + reasonLength
            + "ch, currencies "
            + currencies
            + ", accounts "
            + accounts
            + ", history "
            + historyEntries;
    }

    /** Сборщик потолков из значений конфига. */
    public static final class Builder {

        private int transactionIdLength = DEFAULT_TRANSACTION_ID_LENGTH;
        private int reasonLength = DEFAULT_REASON_LENGTH;
        private int currencies = DEFAULT_CURRENCIES;
        private int accounts = DEFAULT_ACCOUNTS;
        private int historyEntries = HISTORY_ENTRIES_CAP;
        private final List<String> remarks = new ArrayList<>();

        private Builder() {}

        /** Длина {@code transactionId}. Значение выше заводского ужимается до заводского. */
        public Builder transactionIdLength(int value) {
            transactionIdLength = clamp(value, DEFAULT_TRANSACTION_ID_LENGTH, "transactionIdLength");
            return this;
        }

        /** Длина причины. Значение выше заводского ужимается до заводского. */
        public Builder reasonLength(int value) {
            reasonLength = clamp(value, DEFAULT_REASON_LENGTH, "reasonLength");
            return this;
        }

        /** Число валют. Значение выше заводского ужимается до заводского. */
        public Builder currencies(int value) {
            currencies = clamp(value, DEFAULT_CURRENCIES, "currencies");
            return this;
        }

        /** Число счетов. Значение выше заводского ужимается до заводского. */
        public Builder accounts(int value) {
            accounts = clamp(value, DEFAULT_ACCOUNTS, "accounts");
            return this;
        }

        /**
         * Число записей истории. Значение выше заводского ужимается до заводского, ноль и отрицательное
         * считаются отсутствующим и дают заводское.
         */
        public Builder historyEntries(int value) {
            historyEntries = clampPositive(value, HISTORY_ENTRIES_CAP, "historyEntries");
            return this;
        }

        /** Замечания о значениях, которые пришлось заменить заводскими. */
        public List<String> remarks() {
            return new ArrayList<>(remarks);
        }

        /** Готовые потолки. */
        public EconomyLimits build() {
            return new EconomyLimits(transactionIdLength, reasonLength, currencies, accounts, historyEntries);
        }

        private int clamp(int value, int factoryValue, String field) {
            if (value < 0) {
                remarks.add(field + " = " + value + " is below zero, the factory value " + factoryValue + " is used");
                return factoryValue;
            }
            return Math.min(value, factoryValue);
        }

        private int clampPositive(int value, int factoryValue, String field) {
            if (value <= 0) {
                remarks.add(
                    field + " = "
                        + value
                        + " carries no usable ceiling, the factory value "
                        + factoryValue
                        + " is used");
                return factoryValue;
            }
            return Math.min(value, factoryValue);
        }
    }
}
