package com.mrleonardos.codeeconomy.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

import com.mrleonardos.codecore.api.CodeApi;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codeeconomy.EconomyConstants;
import com.mrleonardos.codeeconomy.client.settings.ClientSettings;
import com.mrleonardos.codeeconomy.common.EconomyBridge;
import com.mrleonardos.codeeconomy.common.SideBootstrap;
import com.mrleonardos.codeeconomy.network.EconomyPackets;
import com.mrleonardos.codeeconomy.network.c2s.BalanceRequestPacket;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Клиентская половина: баланс на экране.
 *
 * <p>
 * Серверу о готовности сообщается запросом, и только после него сервер начинает слать суммы. Пока
 * запроса не было, показ ничем не отличается от ванильного клиента, поэтому один и тот же сервер
 * работает и с модом, и без него.
 */
public final class ClientBootstrap implements SideBootstrap {

    private ConfigFile<ClientSettings> settings;
    private ClientBalance balance;
    private BalanceHudRenderer renderer;
    private String askedFor;

    @Override
    public void install() {
        settings = CodeApi.configs()
            .open(
                ConfigSpec.of(EconomyConstants.MODID, EconomyConstants.CLIENT_FILE, ClientSettings.class)
                    .role(ConfigRoles.ECONOMY)
                    .scope(ConfigScope.CLIENT)
                    .build());
        balance = new ClientBalance();
        renderer = new BalanceHudRenderer(Minecraft.getMinecraft(), balance, settings);
        EconomyBridge.client(balance);

        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        renderer.render(event.resolution.getScaledWidth(), event.resolution.getScaledHeight());
    }

    /**
     * Вход в мир поднимает показ, выход его гасит.
     *
     * <p>
     * Забыть баланс при выходе обязательно: на другом сервере счёт другой, а число с прошлого висело бы
     * на экране до первого изменения.
     *
     * <p>
     * Запоминается не сам факт запроса, а валюта, о которой просили. Правка {@code currency} в файле
     * меняет её, и запрос уходит заново: иначе смена валюты ждала бы перезахода в мир.
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (Minecraft.getMinecraft().thePlayer == null) {
            askedFor = null;
            balance.forget();
            return;
        }
        ClientSettings options = settings.get();
        if (!options.enabled) {
            return;
        }
        String wanted = options.currency == null ? "" : options.currency.trim();
        if (wanted.equals(askedFor)) {
            return;
        }
        balance.forget();
        EconomyPackets.channel()
            .toServer(new BalanceRequestPacket(wanted));
        askedFor = wanted;
    }
}
