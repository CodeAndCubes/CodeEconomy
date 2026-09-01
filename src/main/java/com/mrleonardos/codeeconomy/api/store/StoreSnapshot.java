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
 * Состояние денег целиком: и то, что провайдер отдаёт при загрузке, и то, что движок передаёт ему на
 * полную выгрузку.
 *
 * <p>
 * Носит счета, записи журнала и границу {@code checkpointSeq}. При загрузке граница означает «счета
 * учитывают всё до этой записи включительно, дальше идут переигранные записи». При выгрузке
 * ({@link EconomyStore#save} и {@link StoreMaintenance#checkpoint}) движок ставит границей свой
 * {@code lastSeq}: счета в снимке учитывают все проведённые операции, поэтому провайдер вправе
 * записать их вместе с этой границей одной атомарной подменой.
 *
 * <p>
 * Флаг только для чтения закрывает мутации, когда носитель поднялся после аварии, например журнал ушёл
 * в карантин. Расхождения, найденные при переигрывании, приходят отдельным списком: движок пишет их в
 * лог и отдаёт слушателям, но состояние они не меняют.
 */
public final class StoreSnapshot {

    private static final StoreSnapshot EMPTY = new StoreSnapshot(
        Collections.emptyMap(),
        0L,
        Collections.emptyList(),
        Collections.emptyList(),
        false,
        null);

    private final Map<UUID, AccountView> accounts;
    private final long checkpointSeq;
    private final List<TransactionRecord> transactions;
    private final List<String> findings;
    private final boolean readOnly;
    private final String reason;

    private StoreSnapshot(Map<UUID, AccountView> accounts, long checkpointSeq, List<TransactionRecord> transactions,
        List<String> findings, boolean readOnly, String reason) {
        this.accounts = accounts;
        this.checkpointSeq = checkpointSeq;
        this.transactions = transactions;
        this.findings = findings;
        this.readOnly = readOnly;
        this.reason = reason;
    }

    /**
     * Собрать состояние.
     *
     * @param accounts      счета
     * @param checkpointSeq наибольший {@code seq}, который эти счета учитывают
     * @param transactions  записи журнала, включая переигранные после чекпоинта
     */
    public static StoreSnapshot of(Map<UUID, AccountView> accounts, long checkpointSeq,
        List<TransactionRecord> transactions) {
        Objects.requireNonNull(accounts, "accounts");
        Objects.requireNonNull(transactions, "transactions");
        return new StoreSnapshot(
            copyAccounts(accounts),
            checkpointSeq,
            copyTransactions(transactions),
            Collections.emptyList(),
            false,
            null);
    }

    /** Пустое состояние: счетов нет, журнал пуст, граница чекпоинта ноль. */
    public static StoreSnapshot empty() {
        return EMPTY;
    }

    /**
     * То же состояние, но только для чтения.
     *
     * @param whatHappened что случилось, попадает в лог и в ответ администратору
     */
    public StoreSnapshot readOnly(String whatHappened) {
        Objects.requireNonNull(whatHappened, "whatHappened");
        return new StoreSnapshot(accounts, checkpointSeq, transactions, findings, true, whatHappened);
    }

    /**
     * То же состояние с расхождениями, найденными при переигрывании журнала.
     *
     * @param replayFindings расхождения между записанными балансами и переигранными
     */
    public StoreSnapshot withFindings(List<String> replayFindings) {
        Objects.requireNonNull(replayFindings, "replayFindings");
        return new StoreSnapshot(
            accounts,
            checkpointSeq,
            transactions,
            Collections.unmodifiableList(new ArrayList<>(replayFindings)),
            readOnly,
            reason);
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

    /** Расхождения переигрывания: пустой список, когда журнал и балансы сошлись. */
    public List<String> findings() {
        return findings;
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
            && findings.equals(that.findings)
            && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return ((((accounts.hashCode() * 31 + Long.hashCode(checkpointSeq)) * 31 + transactions.hashCode()) * 31
            + findings.hashCode()) * 31 + Boolean.hashCode(readOnly)) * 31 + Objects.hashCode(reason);
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
