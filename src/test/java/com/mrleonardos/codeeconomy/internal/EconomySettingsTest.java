package com.mrleonardos.codeeconomy.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codeeconomy.internal.store.Currencies;

class EconomySettingsTest {

    /** Ключи, которые общие для линейки и живут в главном файле, в секциях storage и audit. */
    private static final List<String> LINEUP_KEYS = Arrays
        .asList("provider", "autosaveSeconds", "logChanges", "logChecks");

    /**
     * Размер страницы зажимается сверху: страница на размер считается при нарезке, и потолок держит
     * произведение в разумных границах вместе с расчётом в long.
     */
    @Test
    void thePageSizeIsClampedFromBothSides() {
        EconomySettings config = EconomyFixtures.settings();
        assertEquals(EconomySettings.DEFAULT_PAGE_SIZE, config.pageSize());

        config.top.pageSize = 5000;
        assertEquals(EconomySettings.MAX_PAGE_SIZE, config.pageSize());

        config.top.pageSize = 0;
        assertEquals(1, config.pageSize());
    }

    @Test
    void theOwnFileLivesInTheEconomyFolderAndIsNamedAfterItsOwner() {
        assertEquals(
            ConfigRoles.ECONOMY,
            EconomySettings.spec()
                .role());
        assertEquals(
            ConfigScope.SETTINGS,
            EconomySettings.spec()
                .scope());
        assertEquals("economy.toml", TestConfigs.fileName(EconomySettings.spec()));
        assertEquals("economy-currencies.toml", TestConfigs.fileName(Currencies.spec()));
    }

    /**
     * Ни один ключ не встречается в обоих файлах. Настройка, уехавшая в главный файл, из конфига мода
     * исчезает: спрашивать одно и то же число из двух мест значит однажды получить два разных ответа.
     */
    @Test
    void noKeyLivesInTwoFilesAtOnce() {
        Set<String> own = keysOf(EconomySettings.class);
        Set<String> main = keysOf(EconomySection.class);
        main.addAll(LINEUP_KEYS);

        for (String key : own) {
            assertFalse(main.contains(key), "ключ " + key + " лежит и в economy.toml, и в главном файле");
        }
        assertTrue(own.contains("maxEntries"));
        assertTrue(own.contains("pageSize"));
        assertTrue(own.contains("failOpen"));
    }

    /** Настройки, уехавшие в главный файл, из конфига мода пропали целиком, включая вес сервиса. */
    @Test
    void whatMovedToTheMainFileIsGoneFromTheModFile() {
        Set<String> own = keysOf(EconomySettings.class);

        for (String moved : Arrays.asList(
            "provider",
            "autosaveSeconds",
            "logChanges",
            "logChecks",
            "defaultCurrency",
            "minTransfer",
            "maxTransfer",
            "payCooldownSeconds",
            "priority")) {
            assertFalse(own.contains(moved), "ключ " + moved + " обязан был уехать из economy.toml");
        }
    }

    /** Имена полей файла настроек вместе с вложенными секциями. */
    private static Set<String> keysOf(Class<?> type) {
        Set<String> keys = new LinkedHashSet<>();
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (field.getType()
                .getName()
                .startsWith(type.getName())) {
                keys.addAll(keysOf(field.getType()));
                continue;
            }
            keys.add(field.getName());
        }
        return keys;
    }
}
