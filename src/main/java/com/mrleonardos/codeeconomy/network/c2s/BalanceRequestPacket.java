package com.mrleonardos.codeeconomy.network.c2s;

import net.minecraft.entity.player.EntityPlayerMP;

import com.mrleonardos.codecore.api.net.Codec;
import com.mrleonardos.codecore.api.net.Packet;
import com.mrleonardos.codecore.api.net.PacketContext;
import com.mrleonardos.codeeconomy.common.EconomyBridge;
import com.mrleonardos.codeeconomy.common.ServerSink;

import io.netty.buffer.ByteBuf;

/**
 * Клиент просит показывать ему баланс и называет валюту.
 *
 * <p>
 * Пакет служит и отметкой готовности: пока он не пришёл, сервер про этого игрока не шлёт ничего. Так
 * один и тот же сервер работает и с полноценным клиентом, и с ванильным.
 *
 * <p>
 * Пустая строка означает валюту сервера по умолчанию. Клиент шлёт пакет при входе в мир и повторяет,
 * когда игрок сменил валюту в своих настройках.
 */
public final class BalanceRequestPacket extends Packet {

    private String currencyId = "";

    public BalanceRequestPacket() {}

    public BalanceRequestPacket(String currencyId) {
        this.currencyId = currencyId == null ? "" : currencyId;
    }

    /** Валюта, за которой хочет следить клиент. */
    public String currencyId() {
        return currencyId;
    }

    @Override
    public void write(ByteBuf buffer) {
        Codec.writeString(buffer, currencyId);
    }

    @Override
    public void read(ByteBuf buffer) {
        currencyId = Codec.readString(buffer);
    }

    @Override
    public void handle(PacketContext context) {
        EntityPlayerMP sender = context.sender();
        ServerSink sink = EconomyBridge.server();
        if (sender != null && sink != null) {
            sink.watch(sender, currencyId);
        }
    }
}
