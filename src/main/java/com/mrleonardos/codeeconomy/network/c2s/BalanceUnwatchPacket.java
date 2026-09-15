package com.mrleonardos.codeeconomy.network.c2s;

import com.mrleonardos.codecore.api.actor.PlayerRef;
import com.mrleonardos.codecore.api.net.CodeBuffer;
import com.mrleonardos.codecore.api.net.Packet;
import com.mrleonardos.codecore.api.net.PacketContext;
import com.mrleonardos.codeeconomy.common.EconomyBridge;
import com.mrleonardos.codeeconomy.common.ServerSink;

/**
 * Клиент просит перестать присылать баланс: показ выключен в его настройках.
 *
 * <p>
 * Без этой просьбы сервер продолжал бы слать пакеты до выхода игрока, хотя локальный экран уже погашен.
 */
public final class BalanceUnwatchPacket extends Packet {

    public BalanceUnwatchPacket() {}

    @Override
    public void write(CodeBuffer buffer) {}

    @Override
    public void read(CodeBuffer buffer) {}

    @Override
    public void handle(PacketContext context) {
        PlayerRef sender = context.player()
            .orElse(null);
        ServerSink sink = EconomyBridge.server();
        if (sender != null && sink != null) {
            sink.unwatch(sender);
        }
    }
}
