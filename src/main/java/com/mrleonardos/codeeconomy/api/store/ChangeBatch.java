package com.mrleonardos.codeeconomy.api.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;

/**
 * Пачка одного коммита для провайдера.
 *
 * <p>
 * Несёт причину, новые версии счетов целиком и записи, которые дописываются в журнал. Провайдер
 * применяет пачку один раз, поэтому частичного применения не бывает: либо всё записано, либо отказ и
 * хранилище в прежнем состоянии. По пачке строится аудит.
 */
public final class ChangeBatch {

    private final ChangeCause cause;
    private final List<AccountView> upserts;
    private final List<TransactionRecord> records;

    private ChangeBatch(ChangeCause cause, List<AccountView> upserts, List<TransactionRecord> records) {
        this.cause = cause;
        this.upserts = upserts;
        this.records = records;
    }

    /** Начать собирать пачку. */
    public static Builder builder(ChangeCause cause) {
        return new Builder(cause);
    }

    /** Причина коммита. */
    public ChangeCause cause() {
        return cause;
    }

    /** Новые версии счетов целиком, в порядке применения. */
    public List<AccountView> upserts() {
        return upserts;
    }

    /** Записи, дописываемые в журнал, в порядке {@code seq}. */
    public List<TransactionRecord> records() {
        return records;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ChangeBatch)) {
            return false;
        }
        ChangeBatch that = (ChangeBatch) other;
        return cause == that.cause && upserts.equals(that.upserts) && records.equals(that.records);
    }

    @Override
    public int hashCode() {
        return (cause.hashCode() * 31 + upserts.hashCode()) * 31 + records.hashCode();
    }

    @Override
    public String toString() {
        return cause + ": " + upserts.size() + " accounts, " + records.size() + " records";
    }

    /** Сборщик пачки. */
    public static final class Builder {

        private final ChangeCause cause;
        private final List<AccountView> upserts = new ArrayList<>();
        private final List<TransactionRecord> records = new ArrayList<>();

        private Builder(ChangeCause cause) {
            this.cause = Objects.requireNonNull(cause, "cause");
        }

        /** Записать счёт целиком. */
        public Builder upsert(AccountView account) {
            upserts.add(Objects.requireNonNull(account, "account"));
            return this;
        }

        /** Дописать запись журнала. */
        public Builder append(TransactionRecord record) {
            records.add(Objects.requireNonNull(record, "record"));
            return this;
        }

        /**
         * Готовая пачка.
         *
         * @throws IllegalArgumentException если в пачке ни одного счёта и ни одной записи
         */
        public ChangeBatch build() {
            if (upserts.isEmpty() && records.isEmpty()) {
                throw new IllegalArgumentException("Change batch needs at least one upsert or record");
            }
            return new ChangeBatch(
                cause,
                Collections.unmodifiableList(new ArrayList<>(upserts)),
                Collections.unmodifiableList(new ArrayList<>(records)));
        }
    }
}
