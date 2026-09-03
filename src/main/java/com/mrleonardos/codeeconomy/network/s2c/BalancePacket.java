package com.mrleonardos.codeeconomy.network.s2c;

import com.mrleonardos.codecore.api.net.Codec;
import com.mrleonardos.codecore.api.net.Packet;
import com.mrleonardos.codecore.api.net.PacketContext;
import com.mrleonardos.codeeconomy.common.ClientSink;
import com.mrleonardos.codeeconomy.common.EconomyBridge;

import io.netty.buffer.ByteBuf;

/**
 * Баланс игрока для показа на экране.
 *
 * <p>
 * Едет числом, а не готовой строкой: сумма в минорных единицах и число знаков после разделителя,
 * печатает её клиент своим {@code Amounts.formatAmount}. Символ валюты и шаблон показа остаются на
 * сервере, они меняются раз в жизни сервера, а пакет ходит на каждое изменение счёта.
 */
public final class BalancePacket extends Packet {

    private String currencyId = "";
    private long amount;
    private int decimals;

    public BalancePacket() {}

    public BalancePacket(String currencyId, long amount, int decimals) {
        this.currencyId = currencyId == null ? "" : currencyId;
        this.amount = amount;
        this.decimals = decimals;
    }

    /** Идентификатор валюты. */
    public String currencyId() {
        return currencyId;
    }

    /** Сумма в минорных единицах. */
    public long amount() {
        return amount;
    }

    /** Сколько младших знаков показывать человеку. */
    public int decimals() {
        return decimals;
    }

    @Override
    public void write(ByteBuf buffer) {
        Codec.writeString(buffer, currencyId);
        buffer.writeLong(amount);
        buffer.writeInt(decimals);
    }

    @Override
    public void read(ByteBuf buffer) {
        currencyId = Codec.readString(buffer);
        amount = buffer.readLong();
        decimals = buffer.readInt();
    }

    @Override
    public void handle(PacketContext context) {
        ClientSink sink = EconomyBridge.client();
        if (sink != null) {
            sink.balance(currencyId, amount, decimals);
        }
    }
}
