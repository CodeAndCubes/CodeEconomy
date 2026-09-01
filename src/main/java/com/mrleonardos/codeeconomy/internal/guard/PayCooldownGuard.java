package com.mrleonardos.codeeconomy.internal.guard;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

import com.mrleonardos.codeeconomy.api.guard.GuardResult;
import com.mrleonardos.codeeconomy.api.guard.TransferGuard;
import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;
import com.mrleonardos.codeeconomy.api.model.TransferRequest;

/**
 * Кулдаун переводов между игроками.
 *
 * <p>
 * Штатный гвард из первой версии: держит паузу {@code limits.payCooldownSeconds} между переводами
 * одного игрока. Отсчёт идёт от записанного перевода, а не от попытки: отказ носителя или веский
 * гвард дальше по цепочке не должны запирать игрока на весь кулдаун за операцию, которой не было.
 *
 * <p>
 * Отметки живут только пока идёт кулдаун: при каждой проверке из карты выбрасывается всё, что старше
 * паузы, поэтому карта не растёт по числу игроков за всё время работы сервера.
 */
public final class PayCooldownGuard implements TransferGuard {

    public static final String ID = "pay-cooldown";
    public static final int DEFAULT_PRIORITY = 1000;

    private final long cooldownMillis;
    private final int priority;
    private final LongSupplier clock;
    private final Map<UUID, Long> lastTransfer = new LinkedHashMap<>();

    public PayCooldownGuard(int cooldownSeconds, LongSupplier clock) {
        this(cooldownSeconds, DEFAULT_PRIORITY, clock);
    }

    public PayCooldownGuard(int cooldownSeconds, int priority, LongSupplier clock) {
        this.cooldownMillis = Math.max(0, cooldownSeconds) * 1000L;
        this.priority = priority;
        this.clock = clock;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public GuardResult check(TransferRequest request, AccountView from, AccountView to) {
        UUID actor = watched(request);
        if (actor == null) {
            return GuardResult.allow();
        }
        long now = clock.getAsLong();
        evictOlderThan(now - cooldownMillis);
        Long previous = lastTransfer.get(actor);
        if (previous != null && now - previous.longValue() < cooldownMillis) {
            return GuardResult.deny("the previous transfer is younger than the cooldown");
        }
        return GuardResult.allow();
    }

    @Override
    public void committed(TransferRequest request) {
        UUID actor = watched(request);
        if (actor == null) {
            return;
        }
        lastTransfer.put(actor, Long.valueOf(clock.getAsLong()));
    }

    /** Автор перевода, за которым гвард следит, или null для операций не про него. */
    private UUID watched(TransferRequest request) {
        if (cooldownMillis <= 0L || request.kind() != TransactionRecord.Kind.TRANSFER) {
            return null;
        }
        return request.actor()
            .orElse(null);
    }

    private void evictOlderThan(long cutoffMillis) {
        Iterator<Map.Entry<UUID, Long>> entries = lastTransfer.entrySet()
            .iterator();
        while (entries.hasNext()) {
            if (entries.next()
                .getValue()
                .longValue() < cutoffMillis) {
                entries.remove();
            }
        }
    }
}
