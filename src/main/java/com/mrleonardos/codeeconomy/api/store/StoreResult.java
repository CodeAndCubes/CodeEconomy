package com.mrleonardos.codeeconomy.api.store;

import java.util.Objects;
import java.util.Optional;

/**
 * Итог работы провайдера: записано либо отказ с причиной из конечного перечня.
 *
 * <p>
 * Отказ приходит значением, а не исключением. Возврат из {@link EconomyStore#apply} означает запись на
 * носителе, поэтому провайдер отвечает отказом только когда операция целиком не состоялась.
 */
public final class StoreResult {

    /** Почему провайдер не записал. */
    public enum Failure {

        /** Носитель не принял запись, операция отклоняется целиком. */
        WRITE_FAILED,

        /** Такую работу провайдер не умеет, например полную выгрузку снимка. */
        UNSUPPORTED
    }

    private static final StoreResult SUCCESS = new StoreResult(null, null);

    private final Failure failure;
    private final String message;

    private StoreResult(Failure failure, String message) {
        this.failure = failure;
        this.message = message;
    }

    /** Записано. */
    public static StoreResult success() {
        return SUCCESS;
    }

    /**
     * Записано.
     *
     * @param message пояснение для журнала
     */
    public static StoreResult success(String message) {
        Objects.requireNonNull(message, "message");
        return new StoreResult(null, message);
    }

    /**
     * Не записано.
     *
     * @param message пояснение для лога и режима деградации
     */
    public static StoreResult failure(Failure failure, String message) {
        Objects.requireNonNull(failure, "failure");
        return new StoreResult(failure, message);
    }

    /** Правда ли запись состоялась. */
    public boolean successful() {
        return failure == null;
    }

    /** Причина отказа или пустой ответ. */
    public Optional<Failure> failure() {
        return Optional.ofNullable(failure);
    }

    /** Пояснение или пустой ответ. */
    public Optional<String> message() {
        return Optional.ofNullable(message);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StoreResult)) {
            return false;
        }
        StoreResult that = (StoreResult) other;
        return failure == that.failure && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(failure) * 31 + Objects.hashCode(message);
    }

    @Override
    public String toString() {
        return successful() ? "success" : failure + ": " + message;
    }
}
