package com.forgeessentials.api;

import com.forgeessentials.api.economy.Economy;

/**
 * Подставная точка входа ForgeEssentials: публичное статическое поле, как в настоящем моде.
 *
 * <p>
 * Поле остаётся пустым, пока тест не поставит туда свою экономику: у настоящего мода оно тоже пустое до
 * подъёма его модуля, и адаптер обязан это переживать.
 */
public class APIRegistry {

    public static Economy economy;
}
