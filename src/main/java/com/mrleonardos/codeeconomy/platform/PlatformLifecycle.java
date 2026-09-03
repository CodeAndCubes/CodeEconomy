package com.mrleonardos.codeeconomy.platform;

import java.util.UUID;

import com.mrleonardos.codeeconomy.internal.EconomyLifecycle;
import com.mrleonardos.codeeconomy.internal.hud.BalanceHud;
import com.mrleonardos.codeeconomy.internal.service.LedgerService;

final class PlatformLifecycle implements EconomyLifecycle {

    private final LedgerService service;
    private final BalanceHud hud;

    PlatformLifecycle(LedgerService service, BalanceHud hud) {
        this.service = service;
        this.hud = hud;
    }

    @Override
    public void onServerStart() {
        service.loadWorld();
    }

    @Override
    public void onServerStop() {
        service.autosave();
        service.close();
    }

    @Override
    public void onJoin(UUID player, String name) {
        service.ledger()
            .markName(player, name);
    }

    @Override
    public void onQuit(UUID player) {
        hud.forget(player);
    }
}
