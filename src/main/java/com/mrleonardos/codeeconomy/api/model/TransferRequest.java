package com.mrleonardos.codeeconomy.api.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.mrleonardos.codeeconomy.api.CurrencyIds;

/**
 * Запрос на мутацию денег: вид операции, стороны, сумма и реквизиты для аудита.
 *
 * <p>
 * Запрос неизменяемый и ничего не делает сам: его проверяет конвейер и либо проводит, либо отвечает
 * кодом отказа. {@code transactionId} обязателен и определяет повтор: в пределах окна идемпотентности
 * (заводские 72 часа, срок задаёт {@code history.idempotencyHours}) одна и та же операция с тем же
 * идентификатором второй раз деньги не двигает. Повтор после выхода окна проводится заново, как
 * новая операция.
 *
 * <p>
 * Сумма в минорных единицах. Для перевода, выдачи и снятия она строго больше нуля, для установки
 * годится любая, включая отрицательную до {@code negativeFloor}, для сброса сумма не нужна.
 *
 * <p>
 * Фабрика проверяет только ссылки и формат идентификатора валюты. Семантические проверки, то есть
 * сумма, длина {@code transactionId} и длина причины, остаются конвейеру: он отвечает кодом
 * {@code BAD_AMOUNT} или {@code INVALID_REQUEST}, а не исключением.
 */
public final class TransferRequest {

    private final TransactionRecord.Kind kind;
    private final UUID from;
    private final UUID to;
    private final long amount;
    private final String currencyId;
    private final String transactionId;
    private final UUID actor;
    private final String reason;

    private TransferRequest(TransactionRecord.Kind kind, UUID from, UUID to, long amount, String currencyId,
        String transactionId, UUID actor, String reason) {
        this.kind = kind;
        this.from = from;
        this.to = to;
        this.amount = amount;
        this.currencyId = currencyId;
        this.transactionId = transactionId;
        this.actor = actor;
        this.reason = reason;
    }

    /**
     * Перевод между двумя счетами.
     *
     * @param actor  кто заказал: uuid игрока или null для консоли
     * @param reason причина словами или null
     */
    public static TransferRequest transfer(UUID from, UUID to, long amount, String currencyId, String transactionId,
        UUID actor, String reason) {
        return build(
            TransactionRecord.Kind.TRANSFER,
            Objects.requireNonNull(from, "from"),
            Objects.requireNonNull(to, "to"),
            amount,
            currencyId,
            transactionId,
            actor,
            reason);
    }

    /**
     * Выдача игроку, источник вне счетов игроков.
     *
     * @param actor  кто заказал: uuid игрока или null для консоли
     * @param reason причина словами или null
     */
    public static TransferRequest deposit(UUID to, long amount, String currencyId, String transactionId, UUID actor,
        String reason) {
        return build(
            TransactionRecord.Kind.DEPOSIT,
            null,
            Objects.requireNonNull(to, "to"),
            amount,
            currencyId,
            transactionId,
            actor,
            reason);
    }

    /**
     * Снятие у игрока, получатель вне счетов игроков.
     *
     * @param actor  кто заказал: uuid игрока или null для консоли
     * @param reason причина словами или null
     */
    public static TransferRequest withdraw(UUID from, long amount, String currencyId, String transactionId, UUID actor,
        String reason) {
        return build(
            TransactionRecord.Kind.WITHDRAW,
            Objects.requireNonNull(from, "from"),
            null,
            amount,
            currencyId,
            transactionId,
            actor,
            reason);
    }

    /**
     * Установка баланса в точное значение, отрицательное допустимо до {@code negativeFloor}.
     *
     * @param actor  кто заказал: uuid игрока или null для консоли
     * @param reason причина словами или null
     */
    public static TransferRequest set(UUID target, long amount, String currencyId, String transactionId, UUID actor,
        String reason) {
        return build(
            TransactionRecord.Kind.SET,
            null,
            Objects.requireNonNull(target, "target"),
            amount,
            currencyId,
            transactionId,
            actor,
            reason);
    }

    /**
     * Возврат баланса к {@code startBalance} валюты, сумма не нужна.
     *
     * @param actor  кто заказал: uuid игрока или null для консоли
     * @param reason причина словами или null
     */
    public static TransferRequest reset(UUID target, String currencyId, String transactionId, UUID actor,
        String reason) {
        return build(
            TransactionRecord.Kind.RESET,
            null,
            Objects.requireNonNull(target, "target"),
            0L,
            currencyId,
            transactionId,
            actor,
            reason);
    }

    private static TransferRequest build(TransactionRecord.Kind kind, UUID from, UUID to, long amount,
        String currencyId, String transactionId, UUID actor, String reason) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(currencyId, "currencyId");
        Objects.requireNonNull(transactionId, "transactionId");
        return new TransferRequest(
            kind,
            from,
            to,
            amount,
            CurrencyIds.checked(currencyId),
            transactionId,
            actor,
            reason);
    }

    /** Вид операции. */
    public TransactionRecord.Kind kind() {
        return kind;
    }

    /** Отправитель или пустой ответ. */
    public Optional<UUID> from() {
        return Optional.ofNullable(from);
    }

    /** Получатель или пустой ответ. */
    public Optional<UUID> to() {
        return Optional.ofNullable(to);
    }

    /** Сумма в минорных единицах, для сброса ноль. */
    public long amount() {
        return amount;
    }

    /** Идентификатор валюты. */
    public String currencyId() {
        return currencyId;
    }

    /** Идентификатор операции для повторов. */
    public String transactionId() {
        return transactionId;
    }

    /** Кто заказал операцию: uuid игрока или пустой ответ для консоли. */
    public Optional<UUID> actor() {
        return Optional.ofNullable(actor);
    }

    /** Причина словами или пустой ответ. */
    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TransferRequest)) {
            return false;
        }
        TransferRequest that = (TransferRequest) other;
        return amount == that.amount && kind == that.kind
            && Objects.equals(from, that.from)
            && Objects.equals(to, that.to)
            && currencyId.equals(that.currencyId)
            && transactionId.equals(that.transactionId)
            && Objects.equals(actor, that.actor)
            && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return ((((((kind.hashCode() * 31 + Objects.hashCode(from)) * 31 + Objects.hashCode(to)) * 31
            + Long.hashCode(amount)) * 31 + currencyId.hashCode()) * 31 + transactionId.hashCode()) * 31
            + Objects.hashCode(actor)) * 31 + Objects.hashCode(reason);
    }

    @Override
    public String toString() {
        return kind + " " + amount + " " + currencyId + " " + transactionId;
    }
}
