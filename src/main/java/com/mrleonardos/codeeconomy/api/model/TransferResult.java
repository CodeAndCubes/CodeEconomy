package com.mrleonardos.codeeconomy.api.model;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * Итог мутации: код из конечного перечня и балансы сторон после попытки.
 *
 * <p>
 * Отказ приходит значением, а не исключением, и всегда несёт балансы, которые видит счёт после
 * попытки. Для {@code DUPLICATE} это балансы после первой, уже проведённой операции: вызывающий
 * проверяет {@code OK} или {@code DUPLICATE} и в обоих случаях деньги на месте.
 */
public final class TransferResult {

    private final ResultCode code;
    private final String transactionId;
    private final Long fromAfter;
    private final Long toAfter;

    private TransferResult(ResultCode code, String transactionId, Long fromAfter, Long toAfter) {
        this.code = code;
        this.transactionId = transactionId;
        this.fromAfter = fromAfter;
        this.toAfter = toAfter;
    }

    /** Операция проведена, {@code OK}. */
    public static TransferResult success(String transactionId, Long fromAfter, Long toAfter) {
        return new TransferResult(ResultCode.OK, transactionId, fromAfter, toAfter);
    }

    /** Повтор известной операции, {@code DUPLICATE} с балансами первого проведения. */
    public static TransferResult duplicate(String transactionId, Long fromAfter, Long toAfter) {
        return new TransferResult(ResultCode.DUPLICATE, transactionId, fromAfter, toAfter);
    }

    /** Отказ без известных балансов. */
    public static TransferResult failure(ResultCode code, String transactionId) {
        return of(code, transactionId, null, null);
    }

    /** Отказ с балансами сторон после попытки. */
    public static TransferResult failure(ResultCode code, String transactionId, Long fromAfter, Long toAfter) {
        return of(code, transactionId, fromAfter, toAfter);
    }

    private static TransferResult of(ResultCode code, String transactionId, Long fromAfter, Long toAfter) {
        Objects.requireNonNull(code, "code");
        if (code == ResultCode.OK || code == ResultCode.DUPLICATE) {
            throw new IllegalArgumentException("Use success or duplicate for " + code);
        }
        Objects.requireNonNull(transactionId, "transactionId");
        return new TransferResult(code, transactionId, fromAfter, toAfter);
    }

    /** Код исхода из конечного перечня. */
    public ResultCode code() {
        return code;
    }

    /** Правда ли операция считается состоявшейся: {@code OK} или {@code DUPLICATE}. */
    public boolean applied() {
        return code == ResultCode.OK || code == ResultCode.DUPLICATE;
    }

    /** Идентификатор операции, тот же, что был в запросе. */
    public String transactionId() {
        return transactionId;
    }

    /** Баланс отправителя после попытки или пустой ответ. */
    public OptionalLong fromAfter() {
        return fromAfter == null ? OptionalLong.empty() : OptionalLong.of(fromAfter);
    }

    /** Баланс получателя после попытки или пустой ответ. */
    public OptionalLong toAfter() {
        return toAfter == null ? OptionalLong.empty() : OptionalLong.of(toAfter);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TransferResult)) {
            return false;
        }
        TransferResult that = (TransferResult) other;
        return code == that.code && transactionId.equals(that.transactionId)
            && Objects.equals(fromAfter, that.fromAfter)
            && Objects.equals(toAfter, that.toAfter);
    }

    @Override
    public int hashCode() {
        return (((code.hashCode() * 31 + transactionId.hashCode()) * 31 + Objects.hashCode(fromAfter)) * 31)
            + Objects.hashCode(toAfter);
    }

    @Override
    public String toString() {
        return code + " " + transactionId;
    }
}
