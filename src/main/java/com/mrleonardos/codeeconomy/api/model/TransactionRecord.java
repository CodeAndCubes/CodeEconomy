package com.mrleonardos.codeeconomy.api.model;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import com.mrleonardos.codeeconomy.api.CurrencyIds;
import com.mrleonardos.codeeconomy.api.EconomyLimits;

/**
 * Одна запись журнала: одна мутация целиком.
 *
 * <p>
 * Перевод это одна запись с обеими сторонами и обоими балансами after, поэтому частичное списание
 * невозможно, а replay журнала сверяет каждый баланс. Порядок задаёт {@code seq}, а не часы: метка
 * времени нужна только для показа человеку.
 *
 * <p>
 * У операции, где стороны две, заполнены {@code from} и {@code to}. Выдача несёт только
 * {@code to}, снятие только {@code from}, установка и сброс только {@code to} с итоговым балансом.
 * Сторона, которой нет, пуста вместе со своим after-балансом.
 */
public final class TransactionRecord {

    /** Версия формата записи, ставится в новые записи, старые читаются своей версией. */
    public static final int CURRENT_VERSION = 1;

    /** Вид операции. */
    public enum Kind {

        /** Перевод между счетами. */
        TRANSFER,

        /** Выдача, источник вне счетов игроков. */
        DEPOSIT,

        /** Снятие, получатель вне счетов игроков. */
        WITHDRAW,

        /** Установка баланса в точное значение. */
        SET,

        /** Возврат баланса к {@code startBalance} валюты. */
        RESET
    }

    private final long seq;
    private final long ts;
    private final int version;
    private final String transactionId;
    private final Kind kind;
    private final String currencyId;
    private final UUID from;
    private final Long fromAfter;
    private final UUID to;
    private final Long toAfter;
    private final ChangeCause cause;
    private final UUID actor;
    private final String reason;

    private TransactionRecord(long seq, long ts, int version, String transactionId, Kind kind, String currencyId,
        UUID from, Long fromAfter, UUID to, Long toAfter, ChangeCause cause, UUID actor, String reason) {
        this.seq = seq;
        this.ts = ts;
        this.version = version;
        this.transactionId = transactionId;
        this.kind = kind;
        this.currencyId = currencyId;
        this.from = from;
        this.fromAfter = fromAfter;
        this.to = to;
        this.toAfter = toAfter;
        this.cause = cause;
        this.actor = actor;
        this.reason = reason;
    }

    /** Начать собирать запись журнала. */
    public static Builder builder(Kind kind, String currencyId, String transactionId) {
        return new Builder(kind, currencyId, transactionId);
    }

    /** Порядковый номер в журнале, растёт монотонно и задаёт порядок операций. */
    public long seq() {
        return seq;
    }

    /** Метка времени в миллисекундах, только для показа. */
    public long ts() {
        return ts;
    }

    /** Версия формата записи. */
    public int version() {
        return version;
    }

    /** Идентификатор операции для повторов. */
    public String transactionId() {
        return transactionId;
    }

    /** Вид операции. */
    public Kind kind() {
        return kind;
    }

    /** Идентификатор валюты. */
    public String currencyId() {
        return currencyId;
    }

    /** Отправитель или пустой ответ. */
    public Optional<UUID> from() {
        return Optional.ofNullable(from);
    }

    /** Баланс отправителя после операции или пустой ответ. */
    public OptionalLong fromAfter() {
        return fromAfter == null ? OptionalLong.empty() : OptionalLong.of(fromAfter);
    }

    /** Получатель или пустой ответ. */
    public Optional<UUID> to() {
        return Optional.ofNullable(to);
    }

    /** Баланс получателя после операции или пустой ответ. */
    public OptionalLong toAfter() {
        return toAfter == null ? OptionalLong.empty() : OptionalLong.of(toAfter);
    }

    /** Причина операции. */
    public ChangeCause cause() {
        return cause;
    }

    /** Кто заказал операцию: uuid игрока или пустой ответ для консоли. */
    public Optional<UUID> actor() {
        return Optional.ofNullable(actor);
    }

    /** Причина словами от того, кто заказал, или пустой ответ. */
    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TransactionRecord)) {
            return false;
        }
        TransactionRecord that = (TransactionRecord) other;
        return seq == that.seq && ts == that.ts
            && version == that.version
            && transactionId.equals(that.transactionId)
            && kind == that.kind
            && currencyId.equals(that.currencyId)
            && Objects.equals(from, that.from)
            && Objects.equals(fromAfter, that.fromAfter)
            && Objects.equals(to, that.to)
            && Objects.equals(toAfter, that.toAfter)
            && cause == that.cause
            && Objects.equals(actor, that.actor)
            && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return ((((((((Long.hashCode(seq) * 31 + Long.hashCode(ts)) * 31 + version) * 31 + transactionId.hashCode())
            * 31 + kind.hashCode()) * 31 + currencyId.hashCode()) * 31 + Objects.hashCode(from)) * 31
            + Objects.hashCode(fromAfter)) * 31 + Objects.hashCode(to)) * 31 + Objects.hashCode(toAfter);
    }

    @Override
    public String toString() {
        return seq + " " + kind + " " + currencyId + " " + transactionId;
    }

    /** Сборщик записи журнала. */
    public static final class Builder {

        private final Kind kind;
        private final String currencyId;
        private final String transactionId;
        private long seq;
        private long ts;
        private int version = CURRENT_VERSION;
        private UUID from;
        private Long fromAfter;
        private UUID to;
        private Long toAfter;
        private ChangeCause cause;
        private UUID actor;
        private String reason;
        private boolean seqSet;
        private boolean tsSet;

        private Builder(Kind kind, String currencyId, String transactionId) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.currencyId = CurrencyIds.checked(currencyId);
            this.transactionId = checkedTransactionId(transactionId);
        }

        /** Порядковый номер, обязателен. */
        public Builder seq(long value) {
            seq = value;
            seqSet = true;
            return this;
        }

        /** Метка времени в миллисекундах, обязательна. */
        public Builder ts(long value) {
            ts = value;
            tsSet = true;
            return this;
        }

        /** Версия формата записи, по умолчанию {@link #CURRENT_VERSION}. */
        public Builder version(int value) {
            version = value;
            return this;
        }

        /** Отправитель и его баланс после операции. */
        public Builder from(UUID player, long after) {
            from = Objects.requireNonNull(player, "player");
            fromAfter = after;
            return this;
        }

        /** Получатель и его баланс после операции. */
        public Builder to(UUID player, long after) {
            to = Objects.requireNonNull(player, "player");
            toAfter = after;
            return this;
        }

        /** Причина операции, обязательна. */
        public Builder cause(ChangeCause value) {
            cause = Objects.requireNonNull(value, "cause");
            return this;
        }

        /** Кто заказал операцию, null значит консоль. */
        public Builder actor(UUID value) {
            actor = value;
            return this;
        }

        /** Причина словами, не длиннее потолка {@link EconomyLimits}. */
        public Builder reason(String value) {
            reason = value;
            return this;
        }

        /**
         * Готовая запись.
         *
         * @throws IllegalArgumentException если нарушены границы полей или стороны не сходятся с видом
         *                                  операции
         */
        public TransactionRecord build() {
            if (!seqSet || seq < 0) {
                throw new IllegalArgumentException("Record needs a non-negative seq");
            }
            if (!tsSet || ts < 0) {
                throw new IllegalArgumentException("Record needs a non-negative ts");
            }
            if (version < 1) {
                throw new IllegalArgumentException("Record version must be positive: " + version);
            }
            if (reason != null && reason.length() > EconomyLimits.defaults()
                .reasonLength()) {
                throw new IllegalArgumentException("Reason is longer than the ceiling: " + reason.length());
            }
            Objects.requireNonNull(cause, "cause");
            if (kind == Kind.TRANSFER) {
                requireBothSides();
            } else if (kind == Kind.DEPOSIT) {
                requireAbsentSide(from, fromAfter, "from");
                requirePresentSide(to, toAfter, "to");
            } else if (kind == Kind.WITHDRAW) {
                requirePresentSide(from, fromAfter, "from");
                requireAbsentSide(to, toAfter, "to");
            } else {
                requireAbsentSide(from, fromAfter, "from");
                requirePresentSide(to, toAfter, "to");
            }
            return new TransactionRecord(
                seq,
                ts,
                version,
                transactionId,
                kind,
                currencyId,
                from,
                fromAfter,
                to,
                toAfter,
                cause,
                actor,
                reason);
        }

        private void requireBothSides() {
            requirePresentSide(from, fromAfter, "from");
            requirePresentSide(to, toAfter, "to");
        }

        private static void requirePresentSide(UUID player, Long after, String side) {
            if (player == null || after == null) {
                throw new IllegalArgumentException("Transaction of this kind needs the " + side + " side");
            }
        }

        private static void requireAbsentSide(UUID player, Long after, String side) {
            if (player != null || after != null) {
                throw new IllegalArgumentException("Transaction of this kind must not carry the " + side + " side");
            }
        }

        private static String checkedTransactionId(String value) {
            EconomyLimits limits = EconomyLimits.defaults();
            Objects.requireNonNull(value, "transactionId");
            if (!limits.acceptsTransactionId(value)) {
                throw new IllegalArgumentException(
                    "Transaction id must be from 1 to " + limits.transactionIdLength() + " symbols: " + value.length());
            }
            return value;
        }
    }
}
