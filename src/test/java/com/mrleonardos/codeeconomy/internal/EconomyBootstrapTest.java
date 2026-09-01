package com.mrleonardos.codeeconomy.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codeeconomy.internal.adapter.ForgeEssentialsAdapter;
import com.mrleonardos.codeeconomy.internal.event.EventDispatcher;
import com.mrleonardos.codeeconomy.internal.service.LedgerService;
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
    private final List<LedgerService> taken = new ArrayList<>();

    private TestConfigs configs;

    @BeforeEach
    void prepare() {
        configs = new TestConfigs(root);
    }

    @Test
    void ourModTakesTheRoleAndOpensItsFiles() {
        EconomyBootstrap bootstrap = declared();
        adapters.decide(ConfigRoles.ECONOMY, TestAdapters.AUTO);

        assertTrue(bootstrap.start(adapters, taken::add));
        assertEquals(EconomyConstants.OWNER, bootstrap.owner());
        assertNotNull(bootstrap.service());
        assertNotNull(bootstrap.config());
        assertEquals(1, taken.size(), "команды, подписки и писатель заводятся ровно один раз");
        assertSame(bootstrap.service(), taken.get(0));
        assertTrue(Files.isRegularFile(settings()), "собственные настройки создаются при первом запуске");
        assertTrue(Files.isRegularFile(currencies()), "валюты создаются при первом запуске");
    }

    /**
     * Отход целиком: ни одного корня команд, поэтому при владельце forgeessentials на сервере нет ни
     * {@code /pay}, ни {@code /eco} с его подкомандами top и history. Пустой ответ вместо «такой команды
     * тут нет» это ровно тот молчаливый отказ, который запрещён.
     */
    @Test
    void anAdapterOwnerMakesTheModStandDown() {
        EconomyBootstrap bootstrap = declared();
        adapters.decide(ConfigRoles.ECONOMY, ForgeEssentialsAdapter.NAME);

        assertFalse(bootstrap.start(adapters, taken::add));
        assertEquals(ForgeEssentialsAdapter.NAME, bootstrap.owner());
        assertTrue(taken.isEmpty(), "ни команд, ни подписок, ни писателя");
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

        assertFalse(bootstrap.start(adapters, taken::add));
        assertNull(bootstrap.owner());
        assertNull(bootstrap.service());
        assertTrue(taken.isEmpty());
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
        bootstrap.start(adapters, taken::add);

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
