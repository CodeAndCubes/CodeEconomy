package com.mrleonardos.codeeconomy.internal.hud;

import java.util.UUID;

/**
 * Куда уходит посчитанный показ.
 *
 * <p>
 * Отправку держит платформа: здесь нет ни игрока Minecraft, ни канала, поэтому решение «слать или нет»
 * проверяется тестом без запуска игры.
 */
public interface BalanceHudSink {

    /**
     * Отправить игроку сумму.
     *
     * @param amount   сумма в минорных единицах
     * @param decimals сколько младших знаков показывать человеку
     */
    void send(UUID player, String currencyId, long amount, int decimals);
}
