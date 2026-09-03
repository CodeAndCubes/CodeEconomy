package com.mrleonardos.codeeconomy.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeeconomy.network.c2s.BalanceRequestPacket;
import com.mrleonardos.codeeconomy.network.s2c.BalancePacket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Запись и чтение пакетов показа.
 *
 * <p>
 * Две стороны связывает только порядок полей, поэтому он и проверяется: расхождение видно здесь, а не
 * кривым числом на экране у игрока.
 */
class EconomyPacketsTest {

    @Test
    void theBalanceSurvivesTheRoundTrip() {
        BalancePacket read = roundTrip(new BalancePacket("coin", 123456L, 2), new BalancePacket());

        assertEquals("coin", read.currencyId());
        assertEquals(123456L, read.amount());
        assertEquals(2, read.decimals());
    }

    @Test
    void aNegativeBalanceSurvivesToo() {
        BalancePacket read = roundTrip(new BalancePacket("credit", -500L, 0), new BalancePacket());

        assertEquals(-500L, read.amount());
        assertEquals(0, read.decimals());
    }

    @Test
    void theRequestCarriesTheCurrency() {
        BalanceRequestPacket read = roundTrip(new BalanceRequestPacket("credit"), new BalanceRequestPacket());

        assertEquals("credit", read.currencyId());
    }

    @Test
    void anEmptyRequestMeansTheDefaultCurrency() {
        BalanceRequestPacket read = roundTrip(new BalanceRequestPacket(null), new BalanceRequestPacket());

        assertEquals("", read.currencyId(), "пустая строка едет по сети, а null там взяться не может");
    }

    private static <T extends com.mrleonardos.codecore.api.net.Packet> T roundTrip(T written, T empty) {
        ByteBuf buffer = Unpooled.buffer();
        written.write(buffer);
        empty.read(buffer);
        assertEquals(0, buffer.readableBytes(), "чтение обязано забрать ровно столько, сколько записала запись");
        return empty;
    }
}
