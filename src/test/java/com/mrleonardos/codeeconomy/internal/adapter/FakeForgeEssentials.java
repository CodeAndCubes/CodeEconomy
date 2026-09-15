package com.mrleonardos.codeeconomy.internal.adapter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.forgeessentials.api.APIRegistry;
import com.forgeessentials.api.UserIdent;
import com.forgeessentials.api.economy.Economy;
import com.forgeessentials.api.economy.Wallet;

/** Экономика ForgeEssentials в памяти: то, что адаптер находит отражением в тестовом classpath. */
public final class FakeForgeEssentials implements Economy {

    /** Как чужой мод называет свою единственную валюту. */
    public static final String CURRENCY_NAME = "Zeny";

    private final Map<UUID, Long> wallets = new LinkedHashMap<>();

    /** Режим тихого провала: кошелёк принимает вызовы и ничего не меняет. */
    public boolean ignoreWrites;

    /** Поставить экономику в чужой реестр, как это делает сам мод при подъёме своего модуля. */
    public static FakeForgeEssentials install() {
        FakeForgeEssentials economy = new FakeForgeEssentials();
        APIRegistry.economy = economy;
        return economy;
    }

    /** Убрать экономику: поле реестра снова пустое, как до подъёма чужого модуля. */
    public static void uninstall() {
        APIRegistry.economy = null;
    }

    public long balanceOf(UUID player) {
        Long amount = wallets.get(player);
        return amount == null ? 0L : amount.longValue();
    }

    public void put(UUID player, long amount) {
        wallets.put(player, Long.valueOf(amount));
    }

    @Override
    public Wallet getWallet(UserIdent player) {
        return new MemoryWallet(player.getUuid());
    }

    @Override
    public String currency(long amount) {
        return CURRENCY_NAME;
    }

    @Override
    public String toString(long amount) {
        return amount + " " + CURRENCY_NAME;
    }

    private final class MemoryWallet implements Wallet {

        private final UUID owner;

        private MemoryWallet(UUID owner) {
            this.owner = owner;
        }

        @Override
        public long get() {
            return balanceOf(owner);
        }

        @Override
        public void set(long value) {
            if (!ignoreWrites) {
                put(owner, value);
            }
        }

        @Override
        public void add(long amount) {
            if (!ignoreWrites) {
                put(owner, balanceOf(owner) + amount);
            }
        }

        @Override
        public void add(double amount) {
            add((long) amount);
        }

        @Override
        public boolean covers(long value) {
            return balanceOf(owner) >= value;
        }

        @Override
        public boolean withdraw(long value) {
            if (!covers(value)) {
                return false;
            }
            put(owner, balanceOf(owner) - value);
            return true;
        }
    }
}
