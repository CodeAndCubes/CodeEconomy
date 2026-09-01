package com.mrleonardos.codeeconomy.internal.guard;

import java.util.HashMap;
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
 * одного игрока. Отсчёт начинается с проверки, потому что гвард отвечает до записи и о её исходе не
 * знает.
 */
public final class PayCooldownGuard implements TransferGuard {

    public static final String ID = "pay-cooldown";
    public static final int DEFAULT_PRIORITY = 1000;

    private final long cooldownMillis;
    private final int priority;
    private final LongSupplier clock;
    private final Map<UUID, Long> lastTransfer = new HashMap<>();

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
        if (cooldownMillis <= 0L || request.kind() != TransactionRecord.Kind.TRANSFER
            || !request.actor()
                .isPresent()) {
            return GuardResult.allow();
        }
        UUID actor = request.actor()
            .get();
        long now = clock.getAsLong();
        Long previous = lastTransfer.get(actor);
        if (previous != null && now - previous.longValue() < cooldownMillis) {
            return GuardResult.deny("the previous transfer is younger than the cooldown");
        }
        lastTransfer.put(actor, Long.valueOf(now));
        return GuardResult.allow();
    }
}
