package com.mrleonardos.codeeconomy.internal.engine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mrleonardos.codeeconomy.api.model.TransactionRecord;

/**
 * Индекс {@code transactionId} в запись журнала.
 *
 * <p>
 * Индекс живёт только в памяти, но восстанавливается из журнала при загрузке, поэтому повтор ловится и
 * после перезапуска. Записи старше окна считаются новыми операциями: повтор такого идентификатора не
 * отклоняется, а проводится заново.
 */
public final class IdempotencyIndex {

    private final Map<String, TransactionRecord> byId = new HashMap<>();

    /** Запись с таким идентификатором в окне или пустой ответ. */
    public Optional<TransactionRecord> find(String transactionId, long now, long windowMillis) {
        TransactionRecord record = byId.get(transactionId);
        if (record == null) {
            return Optional.empty();
        }
        if (windowMillis > 0L && now - record.ts() >= windowMillis) {
            return Optional.empty();
        }
        return Optional.of(record);
    }

    /** Запись с таким идентификатором независимо от окна: чтобы отличить просроченный повтор. */
    public Optional<TransactionRecord> known(String transactionId) {
        return Optional.ofNullable(byId.get(transactionId));
    }

    public void remember(TransactionRecord record) {
        byId.put(record.transactionId(), record);
    }

    /** Убрать записи старше окна, возвращает число вытесненных. */
    public int forgetBefore(long cutoffMillis) {
        int evicted = 0;
        Map<String, TransactionRecord> next = new HashMap<>();
        for (Map.Entry<String, TransactionRecord> entry : byId.entrySet()) {
            if (entry.getValue()
                .ts() < cutoffMillis) {
                evicted++;
                continue;
            }
            next.put(entry.getKey(), entry.getValue());
        }
        byId.clear();
        byId.putAll(next);
        return evicted;
    }

    public void rebuild(List<TransactionRecord> oldestFirst) {
        byId.clear();
        for (TransactionRecord record : oldestFirst) {
            remember(record);
        }
    }

    public int size() {
        return byId.size();
    }
}
