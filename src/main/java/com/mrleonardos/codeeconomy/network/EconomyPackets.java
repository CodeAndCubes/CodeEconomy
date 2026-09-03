package com.mrleonardos.codeeconomy.network;

import com.mrleonardos.codecore.api.CodeApi;
import com.mrleonardos.codecore.api.net.NetChannel;
import com.mrleonardos.codecore.api.net.PacketSide;
import com.mrleonardos.codeeconomy.network.c2s.BalanceRequestPacket;
import com.mrleonardos.codeeconomy.network.s2c.BalancePacket;

/**
 * Регистрация пакетов экономики.
 *
 * <p>
 * Порядок задаёт их номера в протоколе, поэтому строки нельзя менять местами и нельзя вставлять новые
 * в середину, можно только дописывать в конец. Иначе клиент старой версии прочитает пакет не тем
 * классом.
 */
public final class EconomyPackets {

    private static NetChannel channel;

    private EconomyPackets() {}

    public static void register() {
        channel = CodeApi.network()
            .open(EconomyChannels.NETWORK_CHANNEL);

        channel.register(BalancePacket.class, PacketSide.CLIENT_BOUND);
        channel.register(BalanceRequestPacket.class, PacketSide.SERVER_BOUND);
    }

    /** Канал экономики. Доступен после {@link #register()}. */
    public static NetChannel channel() {
        if (channel == null) {
            throw new IllegalStateException("Economy packets are not registered yet");
        }
        return channel;
    }
}
