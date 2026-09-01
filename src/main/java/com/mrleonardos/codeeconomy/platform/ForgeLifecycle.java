package com.mrleonardos.codeeconomy.platform;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;

import com.mrleonardos.codeeconomy.internal.EconomyLifecycle;
import com.mrleonardos.codeeconomy.internal.service.LedgerService;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

final class ForgeLifecycle {

    private final EconomyLifecycle lifecycle;
    private final LedgerService service;
    private final ServerClock clock;
    private final int autosaveTicks;

    ForgeLifecycle(EconomyLifecycle lifecycle, LedgerService service, ServerClock clock, int autosaveTicks) {
        this.lifecycle = lifecycle;
        this.service = service;
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
