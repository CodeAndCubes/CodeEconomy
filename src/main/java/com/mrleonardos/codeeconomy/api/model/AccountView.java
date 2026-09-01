package com.mrleonardos.codeeconomy.api.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Счёт только для чтения: балансы по валютам, последний известный ник, заморозка.
 *
 * <p>
 * Его видят {@code TransferGuard} и чужие моды через хранилище и события. Правок через вид нет: новая
 * версия счёта строится движком и попадает в пачку для хранилища целиком. Валюты, которой у счёта
 * ещё нет, отвечает ноль, потому что счёт создаётся первой мутацией и хранит только настоящие
 * балансы. Отрицательное значение законно: валюта может разрешить уход до {@code negativeFloor}.
 */
public final class AccountView {

    private final UUID uuid;
    private final String name;
    private final Map<String, Long> balances;
    private final boolean frozen;
    private final long createdAt;

    private AccountView(UUID uuid, String name, Map<String, Long> balances, boolean frozen, long createdAt) {
        this.uuid = uuid;
        this.name = name;
        this.balances = balances;
        this.frozen = frozen;
        this.createdAt = createdAt;
    }

    /**
     * Собрать счёт.
     *
     * @param name      последний известный ник или null, если он ещё неизвестен
     * @param balances  балансы в минорных единицах по идентификаторам валют
     * @param createdAt метка создания в миллисекундах
     */
    public static AccountView of(UUID uuid, String name, Map<String, Long> balances, boolean frozen, long createdAt) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(balances, "balances");
        Map<String, Long> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : balances.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "currencyId");
            Objects.requireNonNull(entry.getValue(), "amount");
            copy.put(entry.getKey(), entry.getValue());
        }
        return new AccountView(uuid, name, Collections.unmodifiableMap(copy), frozen, createdAt);
    }

    /** Владелец счёта. */
    public UUID uuid() {
        return uuid;
    }

    /** Последний известный ник или пустой ответ. */
    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    /** Балансы в минорных единицах по идентификаторам валют. */
    public Map<String, Long> balances() {
        return balances;
    }

    /** Баланс в минорных единицах, у незнакомой валюты ноль. */
    public long balance(String currencyId) {
        Objects.requireNonNull(currencyId, "currencyId");
        Long amount = balances.get(currencyId);
        return amount == null ? 0L : amount;
    }

    /** Правда ли счёт заморожен, тогда движение закрыто в обе стороны. */
    public boolean frozen() {
        return frozen;
    }

    /** Метка создания счёта в миллисекундах. */
    public long createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AccountView)) {
            return false;
        }
        AccountView that = (AccountView) other;
        return frozen == that.frozen && createdAt == that.createdAt
            && uuid.equals(that.uuid)
            && Objects.equals(name, that.name)
            && balances.equals(that.balances);
    }

    @Override
    public int hashCode() {
        return (((uuid.hashCode() * 31 + Objects.hashCode(name)) * 31 + balances.hashCode()) * 31
            + Boolean.hashCode(frozen)) * 31 + Long.hashCode(createdAt);
    }

    @Override
    public String toString() {
        return (name != null ? name : uuid.toString()) + " " + balances + (frozen ? " frozen" : "");
    }
}
