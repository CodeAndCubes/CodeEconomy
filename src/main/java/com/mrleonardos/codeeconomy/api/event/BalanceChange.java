package com.mrleonardos.codeeconomy.api.event;

import java.util.Objects;
import java.util.UUID;

/**
 * Итоговое изменение баланса одного игрока по одной валюте.
 *
 * <p>
 * Мутации за тик коалесцируются: три операции игрока за тик дают одно событие с итоговыми значениями
 * до и после, а не три события с промежуточными.
 */
public final class BalanceChange {

    private final UUID player;
    private final String currencyId;
    private final long before;
    private final long after;

    private BalanceChange(UUID player, String currencyId, long before, long after) {
        this.player = player;
        this.currencyId = currencyId;
        this.before = before;
        this.after = after;
    }

    /** Собрать событие. */
    public static BalanceChange of(UUID player, String currencyId, long before, long after) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(currencyId, "currencyId");
        return new BalanceChange(player, currencyId, before, after);
    }

    /** Владелец баланса. */
    public UUID player() {
        return player;
    }

    /** Идентификатор валюты. */
    public String currencyId() {
        return currencyId;
    }

    /** Баланс до пачки операций, в минорных единицах. */
    public long before() {
        return before;
    }

    /** Баланс после пачки операций, в минорных единицах. */
    public long after() {
        return after;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BalanceChange)) {
            return false;
        }
        BalanceChange that = (BalanceChange) other;
        return before == that.before && after == that.after
            && player.equals(that.player)
            && currencyId.equals(that.currencyId);
    }

    @Override
    public int hashCode() {
        return ((player.hashCode() * 31 + currencyId.hashCode()) * 31 + Long.hashCode(before)) * 31
            + Long.hashCode(after);
    }

    @Override
    public String toString() {
        return player + " " + currencyId + " " + before + " -> " + after;
    }
}
