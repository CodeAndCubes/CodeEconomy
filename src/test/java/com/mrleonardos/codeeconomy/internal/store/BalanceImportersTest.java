package com.mrleonardos.codeeconomy.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codeeconomy.api.EconomyLimits;
import com.mrleonardos.codeeconomy.internal.EconomyFixtures;

class BalanceImportersTest {

    private static final String ALICE = "00000000-0000-0000-0000-0000000000a1";
    private static final String BOB = "00000000-0000-0000-0000-0000000000b2";

    @TempDir
    Path root;

    @Test
    void flatJsonMapsUuidsToMinorUnits() throws Exception {
        Path source = write("import.json", "{\"" + ALICE + "\": 1250, \"" + BOB + "\": 100}");

        BalanceImporters.Imported imported = BalanceImporters
            .read(BalanceImporters.FLAT_JSON, source, EconomyFixtures.coin(), EconomyLimits.defaults());

        assertEquals(
            Long.valueOf(1250L),
            imported.accepted()
                .get(uuid(ALICE)));
        assertEquals(
            Long.valueOf(100L),
            imported.accepted()
                .get(uuid(BOB)),
            "flatjson несёт минорные единицы, множителя 10^decimals тут нет");
        assertTrue(
            imported.rejected()
                .isEmpty());
    }

    @Test
    void flatJsonRejectsBrokenLinesOneByOne() throws Exception {
        Path source = write(
            "import.json",
            "{\"" + ALICE
                + "\": -5, \""
                + BOB
                + "\": 100, \"not-a-uuid\": 10, \""
                + "00000000-0000-0000-0000-0000000000c3\": \"much\", \""
                + "00000000-0000-0000-0000-0000000000d4\": 2000000000000}");

        BalanceImporters.Imported imported = BalanceImporters
            .read(BalanceImporters.FLAT_JSON, source, EconomyFixtures.coin(), EconomyLimits.defaults());

        assertEquals(
            1,
            imported.accepted()
                .size());
        assertEquals(
            Long.valueOf(100L),
            imported.accepted()
                .get(uuid(BOB)));
        assertEquals(
            4,
            imported.rejected()
                .size());
        assertTrue(contains(imported, "not-a-uuid"));
        assertTrue(contains(imported, "below the floor"));
        assertTrue(contains(imported, "unusable balance"));
        assertTrue(contains(imported, "above the ceiling"));
    }

    @Test
    void flatJsonReportsAMissingFileWithoutChanges() throws Exception {
        BalanceImporters.Imported imported = BalanceImporters.read(
            BalanceImporters.FLAT_JSON,
            root.resolve("absent.json"),
            EconomyFixtures.coin(),
            EconomyLimits.defaults());

        assertTrue(
            imported.accepted()
                .isEmpty());
        assertEquals(
            1,
            imported.rejected()
                .size());
    }

    @Test
    void essentialsTransfersMajorUnitsByDecimals() throws Exception {
        Path userdata = root.resolve("userdata");
        Files.createDirectories(userdata);
        write(userdata.resolve(ALICE + ".json"), "money: 25.5");
        write(userdata.resolve(BOB + ".json"), "money: 100");
        write(userdata.resolve("00000000-0000-0000-0000-0000000000c3.json"), "unrelated: 5");

        BalanceImporters.Imported imported = BalanceImporters
            .read(BalanceImporters.ESSENTIALS, userdata, EconomyFixtures.coin(), EconomyLimits.defaults());

        assertEquals(
            Long.valueOf(2550L),
            imported.accepted()
                .get(uuid(ALICE)));
        assertEquals(
            Long.valueOf(10000L),
            imported.accepted()
                .get(uuid(BOB)));
        assertEquals(
            1,
            imported.rejected()
                .size());
        assertTrue(contains(imported, "carries no money entry"));
    }

    @Test
    void essentialsRejectsAPathThatIsNotAUserdataDirectory() throws Exception {
        Path file = write("someone.json", "money: 1");

        BalanceImporters.Imported imported = BalanceImporters
            .read(BalanceImporters.ESSENTIALS, file, EconomyFixtures.coin(), EconomyLimits.defaults());

        assertTrue(
            imported.accepted()
                .isEmpty());
        assertEquals(
            1,
            imported.rejected()
                .size());
        assertTrue(contains(imported, "is not a userdata directory"));
    }

    @Test
    void unknownFormatIsNotRead() {
        assertTrue(!BalanceImporters.knows("csv"));
        assertTrue(BalanceImporters.knows(BalanceImporters.FLAT_JSON));
        assertTrue(BalanceImporters.knows(BalanceImporters.ESSENTIALS));
    }

    private Path write(String name, String content) throws Exception {
        Path path = root.resolve(name);
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        return path;
    }

    private static void write(Path path, String content) throws Exception {
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean contains(BalanceImporters.Imported imported, String part) {
        for (String line : imported.rejected()) {
            if (line.contains(part)) {
                return true;
            }
        }
        return false;
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }
}
