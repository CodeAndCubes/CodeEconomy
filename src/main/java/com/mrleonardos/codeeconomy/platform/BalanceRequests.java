package com.mrleonardos.codeeconomy.platform;

import com.mrleonardos.codecore.api.actor.PlayerRef;
import com.mrleonardos.codeeconomy.common.ServerSink;
import com.mrleonardos.codeeconomy.internal.hud.BalanceHud;

/**
 * Просьба клиента превращается в отметку по идентификатору игрока.
 *
 * <p>
 * Кто просит, сервер берёт из обстановки пакета, а не из его содержимого: имя внутри пакета доверия не
 * заслуживает.
 */
final class BalanceRequests implements ServerSink {

    private final BalanceHud hud;

    BalanceRequests(BalanceHud hud) {
        this.hud = hud;
    }

    @Override
    public void watch(PlayerRef player, String currencyId) {
        hud.watch(player.id(), currencyId);
    }
}
