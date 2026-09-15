package com.mrleonardos.codeeconomy.network.s2c;

import com.mrleonardos.codecore.api.net.CodeBuffer;
import com.mrleonardos.codecore.api.net.Packet;
import com.mrleonardos.codecore.api.net.PacketContext;
import com.mrleonardos.codeeconomy.common.ClientSink;
import com.mrleonardos.codeeconomy.common.EconomyBridge;

/**
 * Сервер снял показ: числа на экране быть не должно.
 *
 * <p>
 * Право показа перепроверяется без изменения счёта, и снятая нода гасит экран сразу, а не с первым
 * платежом. Полей у пакета нет: убирать есть что, пересылать нечего.
 */
public final class BalanceHidePacket extends Packet {

    public BalanceHidePacket() {}

    @Override
    public void write(CodeBuffer buffer) {}

    @Override
    public void read(CodeBuffer buffer) {}

    @Override
    public void handle(PacketContext context) {
        ClientSink sink = EconomyBridge.client();
        if (sink != null) {
            sink.hide();
        }
    }
}
