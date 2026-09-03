package com.mrleonardos.codeeconomy.platform;

import java.util.UUID;

import com.mrleonardos.codecore.api.actor.PlayerRef;
import com.mrleonardos.codecore.platform.PlayerRefs;
import com.mrleonardos.codecore.platform.Players;
import com.mrleonardos.codeeconomy.internal.hud.BalanceHudSink;
import com.mrleonardos.codeeconomy.network.EconomyPackets;
import com.mrleonardos.codeeconomy.network.s2c.BalancePacket;

/**
 * Отправка показа живому игроку.
 *
 * <p>
 * Игрок ищется по идентификатору на каждой отправке, а не держится ссылкой: в 1.7.10 при смене
 * измерения и при возрождении сервер создаёт новый объект игрока, и запомненный уехал бы в никуда.
 */
final class BalanceSender implements BalanceHudSink {

    @Override
    public void send(UUID player, String currencyId, long amount, int decimals) {
        PlayerRef online = PlayerRefs.of(Players.online(player));
        if (online == null) {
            return;
        }
        EconomyPackets.channel()
            .toPlayer(new BalancePacket(currencyId, amount, decimals), online);
    }
}
