package com.mrleonardos.codeeconomy.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codeeconomy.internal.adapter.ForgeEssentialsAdapter;
import com.mrleonardos.codeeconomy.internal.event.EventDispatcher;
import com.mrleonardos.codeeconomy.internal.store.Currencies;
import com.mrleonardos.codeeconomy.internal.store.JsonEconomyStore;

/**
 * Три исхода роли {@code economy}: мод работает сам, роль ушла мосту, роль не занята никем.
 *
 * <p>
 * Отход проверяется по диску: пустых файлов рядом с чужой экономикой быть не должно, а те, что лежали
 * раньше, обязаны остаться нетронутыми, иначе возврат владельца обратно не вернул бы деньги.
 */
class EconomyBootstrapTest {

    @TempDir
    Path root;

    private final TestAdapters adapters = new TestAdapters();

    private TestConfigs configs;

    @BeforeEach
    void prepare() {
        configs = new TestConfigs(root);
    }

    @Test
    void ourModTakesTheRoleAndOpensItsFiles() {
        EconomyBootstrap bootstrap = declared();
        adapters.decide(ConfigRoles.ECONOMY, TestAdapters.AUTO);

        assertTrue(bootstrap.decide(adapters));
        assertEquals(EconomyConstants.OWNER, bootstrap.owner());
        assertNotNull(bootstrap.service());
        assertNotNull(bootstrap.config());
        assertTrue(Files.isRegularFile(settings()), "собственные настройки создаются при первом запуске");
        assertTrue(Files.isRegularFile(currencies()), "валюты создаются при первом запуске");
    }

    @Test
    void anAdapterOwnerMakesTheModStandDown() {
        EconomyBootstrap bootstrap = declared();
        adapters.decide(ConfigRoles.ECONOMY, ForgeEssentialsAdapter.NAME);

        assertFalse(bootstrap.decide(adapters));
        assertEquals(ForgeEssentialsAdapter.NAME, bootstrap.owner());
        assertNull(bootstrap.service(), "леджер не собирается вовсе");
        assertTrue(
            configs.opened()
                .isEmpty(),
            "мод не открыл ни одного своего файла: " + configs.opened());
        assertFalse(Files.exists(settings()));
        assertFalse(Files.exists(currencies()));
        assertFalse(Files.exists(accounts()));
    }

    @Test
    void anEmptyRoleMakesTheModStandDownToo() {
        EconomyBootstrap bootstrap = declared();
        adapters.decide(ConfigRoles.ECONOMY, TestAdapters.OFF);

        assertFalse(bootstrap.decide(adapters));
        assertNull(bootstrap.owner());
        assertNull(bootstrap.service());
        assertTrue(
            configs.opened()
                .isEmpty());
    }

    /** Вернули имя в owners, перезапустили, всё на месте: для этого отход не имеет права трогать файлы. */
    @Test
    void filesOfAPreviousRunSurviveTheStandDown() throws IOException {
        Path existing = settings();
        Files.createDirectories(existing.getParent());
        Files.write(existing, "{\"history\":{\"maxEntries\":7}}".getBytes(StandardCharsets.UTF_8));
        FileTime written = Files.getLastModifiedTime(existing);

        EconomyBootstrap bootstrap = declared();
        adapters.decide(ConfigRoles.ECONOMY, ForgeEssentialsAdapter.NAME);
        bootstrap.decide(adapters);

        assertEquals(
            "{\"history\":{\"maxEntries\":7}}",
            new String(Files.readAllBytes(existing), StandardCharsets.UTF_8));
        assertEquals(written, Files.getLastModifiedTime(existing));
    }

    @Test
    void bothOffersAreOnTheTable() {
        declared();

        assertNotNull(adapters.offerNamed(EconomyConstants.OWNER));
        assertNotNull(adapters.offerNamed(ForgeEssentialsAdapter.NAME));
        assertEquals(
            1,
            adapters.roles()
                .size());
    }

    private EconomyBootstrap declared() {
        EconomyBootstrap bootstrap = new EconomyBootstrap(
            configs,
            inline(),
            () -> true,
            () -> 0L,
            System::currentTimeMillis,
            EconomyFixtures.lookup(),
            new EventDispatcher(EconomyFixtures.LOG),
            EconomyFixtures.LOG);
        bootstrap.declare(adapters);
        return bootstrap;
    }

    private Path settings() {
        return configs.path(EconomySettings.spec());
    }

    private Path currencies() {
        return configs.path(Currencies.spec());
    }

    private Path accounts() {
        return configs.path(JsonEconomyStore.spec());
    }

    private static Scheduler inline() {
        return new Scheduler() {

            @Override
            public void onMainThread(Runnable task) {
                task.run();
            }

            @Override
            public void afterTicks(int ticks, Runnable task) {
                task.run();
            }
        };
    }
}
