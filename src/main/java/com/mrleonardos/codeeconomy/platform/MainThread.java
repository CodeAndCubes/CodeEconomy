package com.mrleonardos.codeeconomy.platform;

import java.util.function.BooleanSupplier;

final class MainThread implements BooleanSupplier {

    private volatile Thread server;

    void attach(Thread serverThread) {
        server = serverThread;
    }

    @Override
    public boolean getAsBoolean() {
        Thread known = server;
        return known != null && Thread.currentThread() == known;
    }
}
