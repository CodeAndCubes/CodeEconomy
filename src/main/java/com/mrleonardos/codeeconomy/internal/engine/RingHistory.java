package com.mrleonardos.codeeconomy.internal.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mrleonardos.codeeconomy.api.model.TransactionRecord;

/**
 * Кольцевая история записей журнала.
 *
 * <p>
 * Неизменяемая: каждая запись возвращает новую историю, поэтому чтение из любого потока видит
 * согласованную версию. Граница по числу записей держит память в узде, граница по возрасту
 * выравнивает историю с окном идемпотентности.
 */
public final class RingHistory {

    private static final RingHistory EMPTY = new RingHistory(Collections.<TransactionRecord>emptyList());

    private final List<TransactionRecord> newestFirst;

    private RingHistory(List<TransactionRecord> newestFirst) {
        this.newestFirst = newestFirst;
    }

    public static RingHistory empty() {
        return EMPTY;
    }

    /** История из записей в порядке их появления в журнале, от старой к свежей. */
    public static RingHistory of(List<TransactionRecord> oldestFirst) {
        return of(oldestFirst, Integer.MAX_VALUE);
    }

    /** То же, но хвост держит не больше {@code maxEntries} свежих записей. */
    public static RingHistory of(List<TransactionRecord> oldestFirst, int maxEntries) {
        if (oldestFirst.isEmpty() || maxEntries <= 0) {
            return EMPTY;
        }
        List<TransactionRecord> newestFirst = new ArrayList<>(oldestFirst);
        Collections.reverse(newestFirst);
        while (newestFirst.size() > maxEntries) {
            newestFirst.remove(newestFirst.size() - 1);
        }
        return new RingHistory(Collections.unmodifiableList(newestFirst));
    }

    public RingHistory append(TransactionRecord record, int maxEntries) {
        List<TransactionRecord> next = new ArrayList<>(Math.min(maxEntries, newestFirst.size() + 1));
        next.add(record);
        for (TransactionRecord known : newestFirst) {
            if (next.size() >= maxEntries) {
                break;
            }
            next.add(known);
        }
        return new RingHistory(Collections.unmodifiableList(next));
    }

    public RingHistory evictOlderThan(long cutoffMillis) {
        if (newestFirst.isEmpty()) {
            return this;
        }
        List<TransactionRecord> next = new ArrayList<>(newestFirst.size());
        for (TransactionRecord record : newestFirst) {
            if (record.ts() >= cutoffMillis) {
                next.add(record);
            }
        }
        if (next.size() == newestFirst.size()) {
            return this;
        }
        return new RingHistory(Collections.unmodifiableList(next));
    }

    /** Все записи, свежие раньше. */
    public List<TransactionRecord> records() {
        return newestFirst;
    }

    /** Записи одного игрока, свежие раньше, страница с нуля. */
    public List<TransactionRecord> byPlayer(UUID player, int page, int pageSize) {
        if (page < 0 || pageSize <= 0) {
            return Collections.emptyList();
        }
        int skipped = 0;
        List<TransactionRecord> found = new ArrayList<>();
        for (TransactionRecord record : newestFirst) {
            if (!touches(record, player)) {
                continue;
            }
            if (skipped < page * pageSize) {
                skipped++;
                continue;
            }
            found.add(record);
            if (found.size() >= pageSize) {
                break;
            }
        }
        return found;
    }

    public int size() {
        return newestFirst.size();
    }

    public Optional<TransactionRecord> newest() {
        return newestFirst.isEmpty() ? Optional.<TransactionRecord>empty() : Optional.of(newestFirst.get(0));
    }

    /** Наибольший {@code seq} в истории, для пустой истории ноль. */
    public long newestSeq() {
        long highest = 0L;
        for (TransactionRecord record : newestFirst) {
            highest = Math.max(highest, record.seq());
        }
        return highest;
    }

    public long oldestTs() {
        return newestFirst.isEmpty() ? Long.MAX_VALUE
            : newestFirst.get(newestFirst.size() - 1)
                .ts();
    }

    private static boolean touches(TransactionRecord record, UUID player) {
        return record.from()
            .map(player::equals)
            .orElse(false)
            || record.to()
                .map(player::equals)
                .orElse(false);
    }
}
