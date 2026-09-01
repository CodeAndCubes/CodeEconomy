package com.forgeessentials.api.economy;

/** Подставной кошелёк ForgeEssentials: счёт в {@code long}, дробной части нет. */
public interface Wallet {

    long get();

    void set(long value);

    void add(long amount);

    void add(double amount);

    boolean covers(long value);

    boolean withdraw(long value);
}
