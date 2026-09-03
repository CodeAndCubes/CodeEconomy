package com.mrleonardos.codeeconomy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeeconomy.api.EconomyApi;
import com.mrleonardos.codesides.gate.PackageGate;

/**
 * Границы, которые держат мод собираемым в два jar.
 *
 * <p>
 * Первая: типы Minecraft живут только там, где без них нельзя. Вторая: общий код не ссылается ни на
 * одну из половин, а половины не видят друг друга. Ссылка через границу сторон падает
 * {@code NoClassDefFoundError} уже у игрока, поэтому ловить её надо здесь.
 */
class ImportGateTest {

    private static final String API = "com/mrleonardos/codeeconomy/api";
    private static final String INTERNAL = "com/mrleonardos/codeeconomy/internal";
    private static final String PLATFORM = "com/mrleonardos/codeeconomy/platform";
    private static final String CLIENT = "com/mrleonardos/codeeconomy/client";
    private static final String COMMON = "com/mrleonardos/codeeconomy/common";
    private static final String NETWORK = "com/mrleonardos/codeeconomy/network";

    private static final String[] SERVER_ONLY = { INTERNAL, PLATFORM };

    /**
     * Шире заводского списка гейта: тот знает только про игру, а из api торчали ещё netty вместе с
     * пакетами и слой платформы ядра, который в api-джаре ядра тоже не лежит.
     */
    private static final String[] FOREIGN = { "net/minecraft", "net/minecraftforge", "cpw/mods", "io/netty",
        "org/lwjgl", "com/mojang", "com/mrleonardos/codecore/platform" };

    @Test
    void apiAndInternalHoldNoPlatformTypes() throws IOException {
        List<String> violations = gate(FOREIGN).violations(API, INTERNAL);

        assertTrue(
            violations.isEmpty(),
            () -> "типы игры, netty и слой платформы ядра живут только в platform, чужие ссылки:\n"
                + String.join("\n", violations));
    }

    @Test
    void commonCodeDoesNotReachIntoTheServerHalf() throws IOException {
        List<String> violations = gate(SERVER_ONLY).violations(COMMON, NETWORK);

        assertTrue(
            violations.isEmpty(),
            () -> "общий код попал бы в клиентский jar со ссылкой на вырезанное:\n" + String.join("\n", violations));
    }

    @Test
    void commonCodeDoesNotReachIntoTheClientHalf() throws IOException {
        List<String> violations = gate(CLIENT).violations(COMMON, NETWORK);

        assertTrue(
            violations.isEmpty(),
            () -> "общий код попал бы в серверный jar со ссылкой на вырезанное:\n" + String.join("\n", violations));
    }

    @Test
    void theClientHalfDoesNotSeeTheServerOne() throws IOException {
        List<String> violations = gate(SERVER_ONLY).violations(CLIENT);

        assertTrue(
            violations.isEmpty(),
            () -> "деньги и клей сервера в клиентском jar вырезаны, ссылки на них некуда вести:\n"
                + String.join("\n", violations));
    }

    @Test
    void theServerHalfDoesNotSeeTheClientOne() throws IOException {
        List<String> violations = gate(CLIENT).violations(INTERNAL, PLATFORM);

        assertTrue(
            violations.isEmpty(),
            () -> "рисование в серверном jar вырезано, ссылки на него некуда вести:\n" + String.join("\n", violations));
    }

    @Test
    void theEntryPointStaysCommon() throws IOException {
        PackageGate gate = gate(INTERNAL, PLATFORM, CLIENT);

        for (Class<?> shared : new Class<?>[] { CodeEconomyMod.class, EconomyConstants.class }) {
            List<String> violations = gate.scan(bytesOf(shared));
            assertTrue(
                violations.isEmpty(),
                () -> shared.getSimpleName() + " лежит в обоих jar и не должен знать половин:\n"
                    + String.join("\n", violations));
        }
    }

    @Test
    void eventListenersArePublic() throws IOException {
        List<String> hidden = gate().hiddenListeners();

        assertTrue(hidden.isEmpty(), () -> "классы с @SubscribeEvent обязаны быть public: " + hidden);
    }

    @Test
    void gateNoticesAForbiddenReference() throws IOException {
        List<String> found = gate().scan(PackageGate.foreignSample());

        assertFalse(found.isEmpty(), "гейт обязан ловить ссылку на тип Minecraft");
    }

    private static PackageGate gate(String... forbidden) throws IOException {
        return PackageGate.of(EconomyApi.class, forbidden);
    }

    private static byte[] bytesOf(Class<?> type) throws IOException {
        String resource = "/" + type.getName()
            .replace('.', '/') + ".class";
        try (InputStream classFile = type.getResourceAsStream(resource)) {
            if (classFile == null) {
                throw new IOException("класс " + type.getName() + " не найден среди скомпилированных");
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            for (int read = classFile.read(chunk); read > 0; read = classFile.read(chunk)) {
                bytes.write(chunk, 0, read);
            }
            return bytes.toByteArray();
        }
    }
}
