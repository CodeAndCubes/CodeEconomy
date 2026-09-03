package com.mrleonardos.codeeconomy.common;

/**
 * Связывает пакеты со стороной, которая умеет их обработать.
 *
 * <p>
 * Каждая сторона подставляет свою реализацию при подъёме. На выделенном сервере клиентской половины не
 * существует вовсе, а серверная не поднимается, когда роль {@code economy} держит чужой мод: пакет,
 * пришедший не туда, просто ничего не находит вместо падения по отсутствующему классу.
 */
public final class EconomyBridge {

    private static ClientSink client;
    private static ServerSink server;

    private EconomyBridge() {}

    public static void client(ClientSink sink) {
        client = sink;
    }

    public static void server(ServerSink sink) {
        server = sink;
    }

    /** Клиентская половина или {@code null}, если её здесь нет. */
    public static ClientSink client() {
        return client;
    }

    /** Серверная половина или {@code null}, если её здесь нет. */
    public static ServerSink server() {
        return server;
    }
}
