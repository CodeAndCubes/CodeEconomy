package com.mrleonardos.codeeconomy.internal.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;

/**
 * Неизменяемый снимок состояния денег: счета, история, граница чекпоинта и ревизия.
 *
 * <p>
 * Мутация строит новый снимок целиком, чтение идёт по volatile-ссылке на прежний. Благодаря этому
 * {@code balance} из любого потока видит согласованную версию, а не половину применённой пачки.
 */
public final class LedgerState {

    private static final LedgerState EMPTY = new LedgerState(
        Collections.<UUID, AccountView>emptyMap(),
        RingHistory.empty(),
        0L,
        0L,
        0L);

    private final Map<UUID, AccountView> accounts;
    private final RingHistory history;
    private final long checkpointSeq;
    private final long lastSeq;
    private final long revision;

    private LedgerState(Map<UUID, AccountView> accounts, RingHistory history, long checkpointSeq, long lastSeq,
        long revision) {
        this.accounts = accounts;
        this.history = history;
        this.checkpointSeq = checkpointSeq;
        this.lastSeq = lastSeq;
        this.revision = revision;
    }

    public static LedgerState empty() {
        return EMPTY;
    }

    /** Снимок после загрузки: счета чекпоинта плюс переигранные записи журнала. */
    public static LedgerState of(Map<UUID, AccountView> accounts, long checkpointSeq, List<TransactionRecord> replayed,
        int historyEntries) {
        long highest = checkpointSeq;
        for (TransactionRecord record : replayed) {
            highest = Math.max(highest, record.seq());
        }
        return new LedgerState(copy(accounts), RingHistory.of(replayed, historyEntries), checkpointSeq, highest, 0L);
    }

    public Map<UUID, AccountView> accounts() {
        return accounts;
    }

    public RingHistory history() {
        return history;
    }

    /** Наибольший {@code seq}, попавший в чекпоинт. */
    public long checkpointSeq() {
        return checkpointSeq;
    }

    /** Наибольший выданный {@code seq}: растёт с каждым коммитом и не зависит от вытеснения истории. */
    public long lastSeq() {
        return lastSeq;
    }

    /** Число мутаций с момента загрузки. */
    public long revision() {
        return revision;
    }

    public Optional<AccountView> account(UUID player) {
        return Optional.ofNullable(accounts.get(player));
    }

    LedgerState next(Map<UUID, AccountView> accounts, TransactionRecord record, int historyEntries) {
        return new LedgerState(
            copy(accounts),
            history.append(record, historyEntries),
            checkpointSeq,
            Math.max(record.seq(), lastSeq + 1L),
            revision + 1L);
    }

    LedgerState withHistory(RingHistory replacement) {
        return new LedgerState(accounts, replacement, checkpointSeq, lastSeq, revision);
    }

    LedgerState withAccounts(Map<UUID, AccountView> replacement) {
        return new LedgerState(copy(replacement), history, checkpointSeq, lastSeq, revision + 1L);
    }

    LedgerState withCheckpoint(long seq) {
        return new LedgerState(accounts, history, seq, lastSeq, revision);
    }

    private static Map<UUID, AccountView> copy(Map<UUID, AccountView> accounts) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(accounts));
    }
}
