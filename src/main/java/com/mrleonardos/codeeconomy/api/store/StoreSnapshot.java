package com.mrleonardos.codeeconomy.api.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;

/**
 * Состояние денег целиком, как его отдаёт провайдер при загрузке.
 *
 * <p>
 * Носит чекпоинт счетов, записи журнала и границу чекпоинта: провайдер сам складывает чекпоинт и
 * записи после него, движок принимает результат как данность. Флаг только для чтения закрывает
 * мутации, когда носитель поднялся после аварии, например журнал ушёл в карантин.
 */
public final class StoreSnapshot {

    private static final StoreSnapshot EMPTY = new StoreSnapshot(
        Collections.emptyMap(),
        0L,
        Collections.emptyList(),
        false,
        null);

    private final Map<UUID, AccountView> accounts;
    private final long checkpointSeq;
    private final List<TransactionRecord> transactions;
    private final boolean readOnly;
    private final String reason;

    private StoreSnapshot(Map<UUID, AccountView> accounts, long checkpointSeq, List<TransactionRecord> transactions,
        boolean readOnly, String reason) {
        this.accounts = accounts;
        this.checkpointSeq = checkpointSeq;
        this.transactions = transactions;
        this.readOnly = readOnly;
        this.reason = reason;
    }

    /**
     * Собрать состояние.
     *
     * @param accounts      чекпоинт счетов
     * @param checkpointSeq наибольший {@code seq}, попавший в чекпоинт
     * @param transactions  записи журнала, включая переигранные после чекпоинта
     */
    public static StoreSnapshot of(Map<UUID, AccountView> accounts, long checkpointSeq,
        List<TransactionRecord> transactions) {
        Objects.requireNonNull(accounts, "accounts");
        Objects.requireNonNull(transactions, "transactions");
        return new StoreSnapshot(copyAccounts(accounts), checkpointSeq, copyTransactions(transactions), false, null);
    }

    /** Пустое состояние: счетов нет, журнал пуст, граница чекпоинта ноль. */
    public static StoreSnapshot empty() {
        return EMPTY;
    }

    /**
     * То же состояние, но только для чтения.
     *
     * @param reason что случилось, попадает в лог
     */
    public static StoreSnapshot readOnly(StoreSnapshot source, String reason) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(reason, "reason");
        return new StoreSnapshot(source.accounts, source.checkpointSeq, source.transactions, true, reason);
    }

    /** Счета по владельцам. */
    public Map<UUID, AccountView> accounts() {
        return accounts;
    }

    /** Наибольший {@code seq}, попавший в чекпоинт. */
    public long checkpointSeq() {
        return checkpointSeq;
    }

    /** Записи журнала в порядке {@code seq}. */
    public List<TransactionRecord> transactions() {
        return transactions;
    }

    /** Правда ли мутации закрыты. */
    public boolean readOnly() {
        return readOnly;
    }

    /** Причина чтения без записи или пустой ответ. */
    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }

    /** Наибольший {@code seq} в состоянии: граница чекпоинта или последняя запись журнала. */
    public long lastSeq() {
        long highest = checkpointSeq;
        for (TransactionRecord record : transactions) {
            if (record.seq() > highest) {
                highest = record.seq();
            }
        }
        return highest;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StoreSnapshot)) {
            return false;
        }
        StoreSnapshot that = (StoreSnapshot) other;
        return checkpointSeq == that.checkpointSeq && readOnly == that.readOnly
            && accounts.equals(that.accounts)
            && transactions.equals(that.transactions)
            && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return (((accounts.hashCode() * 31 + Long.hashCode(checkpointSeq)) * 31 + transactions.hashCode()) * 31
            + Boolean.hashCode(readOnly)) * 31 + Objects.hashCode(reason);
    }

    @Override
    public String toString() {
        return accounts.size() + " accounts, checkpoint "
            + checkpointSeq
            + ", "
            + transactions.size()
            + " records"
            + (readOnly ? ", readonly" : "");
    }

    private static Map<UUID, AccountView> copyAccounts(Map<UUID, AccountView> accounts) {
        Map<UUID, AccountView> copy = new LinkedHashMap<>();
        for (Map.Entry<UUID, AccountView> entry : accounts.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "uuid");
            Objects.requireNonNull(entry.getValue(), "account");
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    private static List<TransactionRecord> copyTransactions(List<TransactionRecord> transactions) {
        for (TransactionRecord record : transactions) {
            Objects.requireNonNull(record, "record");
        }
        return Collections.unmodifiableList(new ArrayList<>(transactions));
    }
}
