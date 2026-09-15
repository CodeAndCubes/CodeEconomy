package com.mrleonardos.codeeconomy.platform;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;

import com.mrleonardos.codeeconomy.internal.EconomyLifecycle;
import com.mrleonardos.codeeconomy.internal.hud.BalanceHud;
import com.mrleonardos.codeeconomy.internal.service.LedgerService;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class ForgeLifecycle {

    private static final long HUD_RECHECK_TICKS = 100L;

    private final EconomyLifecycle lifecycle;
    private final LedgerService service;
    private final BalanceHud hud;
    private final ServerClock clock;
    private final int autosaveTicks;

    ForgeLifecycle(EconomyLifecycle lifecycle, LedgerService service, BalanceHud hud, ServerClock clock,
        int autosaveTicks) {
        this.lifecycle = lifecycle;
        this.service = service;
        this.hud = hud;
        this.clock = clock;
        this.autosaveTicks = Math.max(1, autosaveTicks);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        clock.advance();
        service.tick();
        if (clock.getAsLong() % HUD_RECHECK_TICKS == 0L) {
            hud.recheck();
        }
        if (clock.getAsLong() % autosaveTicks == 0L) {
            service.autosave();
        }
    }

    @SubscribeEvent
    public void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        lifecycle.onJoin(id(event.player), name(event.player));
    }

    @SubscribeEvent
    public void onQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        lifecycle.onQuit(id(event.player));
    }

    private static UUID id(EntityPlayer player) {
        return player.getUniqueID();
    }

    private static String name(EntityPlayer player) {
        return player.getCommandSenderName();
    }
}
