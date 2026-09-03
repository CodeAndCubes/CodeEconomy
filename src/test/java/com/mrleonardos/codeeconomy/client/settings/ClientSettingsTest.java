package com.mrleonardos.codeeconomy.client.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codeeconomy.EconomyConstants;
import com.mrleonardos.codeeconomy.internal.TestConfigs;

/** Где лежит файл показа и что мод делает с кривыми значениями в нём. */
class ClientSettingsTest {

    @TempDir
    Path root;

    @Test
    void preferencesLiveInTheClientSubfolder() {
        ConfigFile<ClientSettings> file = TestConfigs.of(root)
            .open(spec());

        assertEquals(
            root.resolve("config")
                .resolve("code")
                .resolve("economy")
                .resolve("client")
                .resolve("economy-client.toml"),
            file.path(),
            "предпочтения игрока лежат отдельно от настроек сервера");
    }

    @Test
    void everyKeyCarriesItsDescription() {
        ConfigFile<ClientSettings> file = TestConfigs.of(root)
            .open(spec());
        file.save();

        String text = TestConfigs.read(file.path());
        assertTrue(
            text.contains("# Угол экрана: top-left, top-right, bottom-left, bottom-right."),
            "перечень углов должен стоять над ключом, иначе его негде прочитать");
        assertTrue(
            text.contains("# Какая валюта показывается. Пусто означает валюту сервера по умолчанию."),
            "смысл пустого значения объясняется в файле");
    }

    @Test
    void everyCornerNameIsUnderstood() {
        for (HudCorner corner : HudCorner.values()) {
            assertEquals(corner, HudCorner.of(corner.id()));
            assertEquals(
                corner,
                HudCorner.of(
                    corner.id()
                        .toUpperCase()),
                "регистр в файле человек не обязан соблюдать");
        }
    }

    @Test
    void anUnknownCornerFallsBackToTheDefault() {
        ClientSettings settings = new ClientSettings();
        settings.corner = "верхний правый";

        assertEquals(HudCorner.TOP_RIGHT, settings.corner(), "опечатка не должна ронять отрисовку каждый кадр");
        assertEquals(HudCorner.TOP_RIGHT, HudCorner.of(null));
        assertEquals(HudCorner.TOP_RIGHT, HudCorner.of("  "));
    }

    @Test
    void theScaleStaysInsideItsBounds() {
        ClientSettings settings = new ClientSettings();

        settings.scale = 0F;
        assertEquals(ClientSettings.MIN_SCALE, settings.scale(), 0F, "на нуле надпись пропала бы совсем");
        settings.scale = 20F;
        assertEquals(ClientSettings.MAX_SCALE, settings.scale(), 0F, "на двадцати она заняла бы весь экран");
        settings.scale = 1.5F;
        assertEquals(1.5F, settings.scale(), 0F);
    }

    @Test
    void factoryValuesShowTheBalanceInTheCorner() {
        ClientSettings settings = new ClientSettings();

        assertTrue(settings.enabled, "заводской показ включён: ради него ставят клиентскую часть");
        assertEquals(HudCorner.TOP_RIGHT, settings.corner());
        assertEquals("", settings.currency, "пусто означает валюту сервера, а не отсутствие показа");
        assertTrue(settings.hideWithGui, "поверх открытого сундука надпись мешала бы");
    }

    private static ConfigSpec<ClientSettings> spec() {
        return ConfigSpec.of(EconomyConstants.MODID, EconomyConstants.CLIENT_FILE, ClientSettings.class)
            .role(ConfigRoles.ECONOMY)
            .scope(ConfigScope.CLIENT)
            .build();
    }
}
