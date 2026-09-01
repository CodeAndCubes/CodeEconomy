package com.mrleonardos.codeeconomy.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codeeconomy.api.EconomyLimits;
import com.mrleonardos.codeeconomy.api.model.ResultCode;
import com.mrleonardos.codeeconomy.internal.EconomyFixtures;
import com.mrleonardos.codeeconomy.internal.EconomySettings;
import com.mrleonardos.codeeconomy.internal.TestConfigs;
import com.mrleonardos.codeeconomy.internal.command.EconomyMessages;
import com.mrleonardos.codeeconomy.internal.command.MaintenanceOutcome;
import com.mrleonardos.codeeconomy.internal.service.LedgerService;
import com.mrleonardos.codeeconomy.internal.store.JsonEconomyStore;

class PlatformMaintenanceImportTest {

    private static final String ALICE = "00000000-0000-0000-0000-0000000000a1";
    private static final String BOB = "00000000-0000-0000-0000-0000000000b2";

    @TempDir
    Path root;

    private final AtomicLong now = new AtomicLong(1000L);

    @Test
    void dryRunOnlyReportsAndLeavesTheWorldAlone() throws Exception {
        Path source = source();
        LedgerService service = service();

        MaintenanceOutcome outcome = maintenance(service).importBalances("flatjson", source.toString(), false);

        assertTrue(outcome.successful());
        assertEquals(
            Long.valueOf(2L),
            outcome.values()
                .get(0),
            "в отчёт попадают обе строки источника");
        assertEquals(25000L, service.balance(EconomyFixtures.ALICE, "coin"), "без --apply балансы не трогаются");
        assertFalse(Files.exists(journal()), "без --apply журнал не появляется");
        assertTrue(Files.exists(source), "источник импорта остаётся на месте");
    }

    @Test
    void applyWritesOneMigrationRecordPerAccount() throws Exception {
        LedgerService service = service();

        MaintenanceOutcome outcome = maintenance(service).importBalances("flatjson", source().toString(), true);

        assertTrue(outcome.successful());
        assertEquals(1250L, service.balance(EconomyFixtures.ALICE, "coin"));
        assertEquals(10000L, service.balance(EconomyFixtures.BOB, "coin"));
        String[] lines = journalLines();
        assertEquals(2, lines.length, "одна запись на аккаунт");
        assertTrue(lines[0].contains("\"cause\":\"MIGRATION\""));
        assertTrue(lines[0].contains("import:coin:"));
    }

    @Test
    void repeatedImportWritesNothingNew() throws Exception {
        LedgerService service = service();
        maintenance(service).importBalances("flatjson", source().toString(), true);
        int before = journalLines().length;

        MaintenanceOutcome outcome = maintenance(service).importBalances("flatjson", source().toString(), true);

        assertTrue(outcome.successful());
        assertEquals(before, journalLines().length, "повторный импорт дублей в журнал не пишет");
        assertEquals(1250L, service.balance(EconomyFixtures.ALICE, "coin"));
    }

    @Test
    void rejectedLinesAreListedPerLine() throws Exception {
        Path source = write("broken.json", "{\"" + ALICE + "\": -5, \"" + BOB + "\": 100}");

        MaintenanceOutcome outcome = maintenance(service()).importBalances("flatjson", source.toString(), false);

        assertTrue(outcome.successful());
        assertEquals(
            1,
            outcome.rows()
                .size(),
            "отклонённая строка объясняется отдельно");
        assertEquals(
            EconomyMessages.IMPORT_SKIP,
            outcome.rows()
                .get(0)
                .key());
        assertTrue(
            outcome.rows()
                .get(0)
                .arguments()
                .get(0)
                .toString()
                .contains(ALICE));
    }

    @Test
    void freezeOfAnUnknownAccountIsRefused() {
        MaintenanceOutcome outcome = maintenance(service()).freeze(EconomyFixtures.CAROL, true, null, "tx1");

        assertFalse(outcome.successful());
        assertEquals(
            ResultCode.UNKNOWN_PLAYER,
            outcome.code()
                .get());
    }

    private PlatformMaintenance maintenance(LedgerService service) {
        return new PlatformMaintenance(service, EconomyLimits.defaults(), () -> root, EconomyFixtures.LOG);
    }

    private LedgerService service() {
        EconomySettings config = EconomyFixtures.settings();
        // другой тест держит в общем реестре EconomyApi чужого провайдера с именем json, поэтому
        // зовём несуществующее имя: сервис возьмёт встроенного провайдера и будет писать журнал сюда
        config.storage.provider = "builtin-under-test";
        TestConfigs files = new TestConfigs(root);
        return LedgerService.create(
            config,
            Collections.singletonList(EconomyFixtures.coin()),
            files.open(JsonEconomyStore.spec()),
            new com.mrleonardos.codecore.api.util.Scheduler() {

                @Override
                public void onMainThread(Runnable task) {
                    task.run();
                }

                @Override
                public void afterTicks(int ticks, Runnable task) {
                    task.run();
                }
            },
            () -> true,
            () -> 0L,
            now::get,
            EconomyFixtures.lookup(),
            EconomyFixtures.LOG);
    }

    private Path source() throws Exception {
        return write("import.json", "{\"" + ALICE + "\": 12.50, \"" + BOB + "\": 100}");
    }

    private Path write(String name, String content) throws Exception {
        Path path = root.resolve(name);
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        return path;
    }

    private Path journal() {
        return new TestConfigs(root).worldPath("codeeconomy", "accounts")
            .getParent()
            .resolve("journal.jsonl");
    }

    private String[] journalLines() throws Exception {
        List<String> lines = Files.readAllLines(journal(), StandardCharsets.UTF_8);
        return lines.toArray(new String[0]);
    }
}
