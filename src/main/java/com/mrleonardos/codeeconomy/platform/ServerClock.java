package com.mrleonardos.codeeconomy.platform;

import java.util.function.LongSupplier;

final class ServerClock implements LongSupplier {

    private long ticks;

    @Override
    public long getAsLong() {
        return ticks;
    }

    void advance() {
        ticks++;
    }
}
