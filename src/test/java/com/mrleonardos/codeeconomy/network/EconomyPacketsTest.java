package com.mrleonardos.codeeconomy.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.net.CodeBuffer;
import com.mrleonardos.codecore.api.net.Packet;
import com.mrleonardos.codeeconomy.network.c2s.BalanceRequestPacket;
import com.mrleonardos.codeeconomy.network.s2c.BalancePacket;

/**
 * Запись и чтение пакетов показа.
 *
 * <p>
 * Две стороны связывает только порядок полей, поэтому он и проверяется: расхождение видно здесь, а не
 * кривым числом на экране у игрока.
 *
 * <p>
 * Буфер здесь свой, поверх массива байт: пакеты работают с {@link CodeBuffer}, и сетевая библиотека для
 * проверки порядка полей не нужна вовсе.
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

    private static <T extends Packet> T roundTrip(T written, T empty) {
        ArrayBuffer buffer = new ArrayBuffer();
        written.write(buffer);
        empty.read(buffer);
        assertEquals(0, buffer.readableBytes(), "чтение обязано забрать ровно столько, сколько записала запись");
        return empty;
    }

    /** Буфер поверх массива байт: запись двигает позицию, чтение идёт своим курсором с начала. */
    private static final class ArrayBuffer implements CodeBuffer {

        private final ByteBuffer bytes = ByteBuffer.allocate(4096);

        private int read;

        @Override
        public void writeInt(int value) {
            bytes.putInt(value);
        }

        @Override
        public int readInt() {
            int value = bytes.getInt(read);
            read += Integer.BYTES;
            return value;
        }

        @Override
        public void writeLong(long value) {
            bytes.putLong(value);
        }

        @Override
        public long readLong() {
            long value = bytes.getLong(read);
            read += Long.BYTES;
            return value;
        }

        @Override
        public void writeBoolean(boolean value) {
            bytes.put((byte) (value ? 1 : 0));
        }

        @Override
        public boolean readBoolean() {
            return bytes.get(read++) != 0;
        }

        @Override
        public void writeBytes(byte[] value) {
            bytes.put(value);
        }

        @Override
        public void readBytes(byte[] into) {
            for (int index = 0; index < into.length; index++) {
                into[index] = bytes.get(read++);
            }
        }

        @Override
        public int readableBytes() {
            return bytes.position() - read;
        }
    }
}
