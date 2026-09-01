package com.forgeessentials.api.economy;

import com.forgeessentials.api.UserIdent;

/** Подставная экономика ForgeEssentials: сигнатуры дословные, валюта одна, счёт целый. */
public interface Economy {

    Wallet getWallet(UserIdent player);

    String currency(long amount);

    String toString(long amount);
}
