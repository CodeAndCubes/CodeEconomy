package com.mrleonardos.codeeconomy.common;

/**
 * Загружает половину мода по имени класса.
 *
 * <p>
 * Тот же приём, что у {@code @SidedProxy}: строка вместо ссылки. Разница в том, что серверная половина
 * нужна не только выделенному серверу, но и встроенному, который поднимается в одиночной игре, поэтому
 * выбирать её по физической стороне нельзя: решает наличие класса в сборке.
 */
public final class SideParts {

    private static final String SERVER_INSTALLER = "com.mrleonardos.codeeconomy.platform.ServerBootstrap";

    private SideParts() {}

    /**
     * Серверная половина или {@code null}, если её вырезали из этой сборки.
     *
     * @throws IllegalStateException если класс есть, но создать его не удалось: о такой поломке нельзя
     *                               молчать, иначе сервер поднимется без денег
     */
    public static ServerInstaller serverInstaller() {
        Class<?> type;
        try {
            type = Class.forName(SERVER_INSTALLER);
        } catch (ClassNotFoundException clientOnlyBuild) {
            return null;
        }
        try {
            return (ServerInstaller) type.newInstance();
        } catch (ReflectiveOperationException broken) {
            throw new IllegalStateException("Server side of CodeEconomy could not be created", broken);
        }
    }
}
