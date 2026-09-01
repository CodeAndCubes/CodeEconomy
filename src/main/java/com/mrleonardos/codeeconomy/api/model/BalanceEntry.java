package com.mrleonardos.codeeconomy.api.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Строка топа или ответ о балансе: владелец, последний известный ник и сумма в минорных единицах.
 */
public final class BalanceEntry {

    private final UUID player;
    private final String name;
    private final long amount;

    private BalanceEntry(UUID player, String name, long amount) {
        this.player = player;
        this.name = name;
        this.amount = amount;
    }

    /**
     * Собрать строку.
     *
     * @param name последний известный ник или null, если он ещё неизвестен
     */
    public static BalanceEntry of(UUID player, String name, long amount) {
        Objects.requireNonNull(player, "player");
        return new BalanceEntry(player, name, amount);
    }

    /** Владелец баланса. */
    public UUID player() {
        return player;
    }

    /** Последний известный ник или пустой ответ. */
    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    /** Сумма в минорных единицах. */
    public long amount() {
        return amount;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BalanceEntry)) {
            return false;
        }
        BalanceEntry that = (BalanceEntry) other;
        return amount == that.amount && player.equals(that.player) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return (player.hashCode() * 31 + Objects.hashCode(name)) * 31 + Long.hashCode(amount);
    }

    @Override
    public String toString() {
        return (name != null ? name : player.toString()) + "=" + amount;
    }
}
