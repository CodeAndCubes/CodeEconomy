package com.mrleonardos.codeeconomy.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codeeconomy.EconomyConstants;
import com.mrleonardos.codeeconomy.api.EconomyLimits;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.api.model.ResultCode;
import com.mrleonardos.codeeconomy.internal.EconomyConfig;
import com.mrleonardos.codeeconomy.internal.EconomyFixtures;
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

        MaintenanceOutcome outcome = maintenance(service).importBalances("flatjson", "import.json", false);

        assertTrue(outcome.successful());
        assertEquals(
            Long.valueOf(2L),
            outcome.values()
                .get(0),
            "в отчёт попадают обе строки источника");
        assertEquals(
            2,
            outcome.rows()
                .size(),
            "сухой прогон показывает принятые пары построчно");
        assertEquals(
            EconomyMessages.IMPORT_ROW,
            outcome.rows()
                .get(0)
                .key());
        assertEquals(25000L, service.balance(EconomyFixtures.ALICE, "coin"), "без --apply балансы не трогаются");
        assertFalse(Files.exists(journal()), "без --apply журнал не появляется");
        assertTrue(Files.exists(source), "источник импорта остаётся на месте");
    }

    @Test
    void applyWritesOneMigrationRecordPerAccount() throws Exception {
        source();
        LedgerService service = service();

        MaintenanceOutcome outcome = maintenance(service).importBalances("flatjson", "import.json", true);

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
        source();
        LedgerService service = service();
        maintenance(service).importBalances("flatjson", "import.json", true);
        int before = journalLines().length;

        MaintenanceOutcome outcome = maintenance(service).importBalances("flatjson", "import.json", true);

        assertTrue(outcome.successful());
        assertEquals(before, journalLines().length, "повторный импорт дублей в журнал не пишет");
        assertEquals(1250L, service.balance(EconomyFixtures.ALICE, "coin"));
    }

    @Test
    void rejectedLinesAreListedPerLine() throws Exception {
        write("broken.json", "{\"" + ALICE + "\": -5, \"" + BOB + "\": 100}");

        MaintenanceOutcome outcome = maintenance(service()).importBalances("flatjson", "broken.json", false);

        assertTrue(outcome.successful());
        assertEquals(
            2,
            outcome.rows()
                .size(),
            "принятая пара и отклонённая строка объясняются отдельно");
        assertEquals(
            EconomyMessages.IMPORT_SKIP,
            outcome.rows()
                .get(1)
                .key());
        assertTrue(
            outcome.rows()
                .get(1)
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
        TestConfigs files = TestConfigs.of(root);
        List<CurrencyRecord> currencies = Collections.singletonList(EconomyFixtures.coin());
        EconomyConfig config = EconomyFixtures.configs()
            .build();
        return LedgerService.create(
            EconomyFixtures.jsonStore(files, currencies, config.idempotencyMillis(), now::get),
            config,
            currencies,
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
            new com.mrleonardos.codeeconomy.internal.event.EventDispatcher(EconomyFixtures.LOG),
            EconomyFixtures.LOG);
    }

    private Path source() throws Exception {
        return write("import.json", "{\"" + ALICE + "\": 1250, \"" + BOB + "\": 10000}");
    }

    private Path write(String name, String content) throws Exception {
        Path path = root.resolve(name);
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        return path;
    }

    private Path journal() {
        return TestConfigs.of(root)
            .path(JsonEconomyStore.spec())
            .getParent()
            .resolve(EconomyConstants.JOURNAL_FILE);
    }

    private String[] journalLines() throws Exception {
        List<String> lines = Files.readAllLines(journal(), StandardCharsets.UTF_8);
        return lines.toArray(new String[0]);
    }

    /**
     * Причина записи склеивается из формата и имени файла и упиралась в потолок причины: каждая строка
     * отклонялась INVALID_REQUEST на разборе, а отчёт показывал ноль перенесённых без объяснения.
     */
    @Test
    void aLongSourceNameDoesNotBlockTheWholeImport() throws Exception {
        StringBuilder name = new StringBuilder();
        for (int index = 0; index < 150; index++) {
            name.append('n');
        }
        write(name + ".json", "{\"" + ALICE + "\": 1250}");
        LedgerService service = service();

        MaintenanceOutcome outcome = maintenance(service).importBalances("flatjson", name + ".json", true);

        assertTrue(outcome.successful());
        assertEquals(
            Long.valueOf(1L),
            outcome.values()
                .get(0),
            "длинное имя источника перенос не ломает");
        assertEquals(1250L, service.balance(EconomyFixtures.ALICE, "coin"));
    }

    /**
     * Право на импорт не открывает произвольные файлы диска: абсолютный путь и выход через {@code ..}
     * отвергаются, разбирается только то, что лежит в папке источника.
     */
    @Test
    void anAbsoluteOrEscapingSourceIsRefused() throws Exception {
        Path source = source();
        PlatformMaintenance maintenance = maintenance(service());

        assertEquals(
            ResultCode.INVALID_REQUEST,
            maintenance.importBalances("flatjson", source.toString(), false)
                .code()
                .get(),
            "абсолютный путь отвергнут");
        assertEquals(
            ResultCode.INVALID_REQUEST,
            maintenance.importBalances("flatjson", "../" + source.getFileName(), false)
                .code()
                .get(),
            "выход за папку источника отвергнут");
        assertFalse(Files.exists(journal()), "отвергнутый путь ничего не читает и не пишет");
    }

    /** Сто нажатий это не сто полных сверок: пока прошлая идёт, новая отвечает занятостью. */
    @Test
    void aSecondVerifyWhileOneRunsAnswersBusy() {
        List<Runnable> held = new ArrayList<>();
        PlatformMaintenance maintenance = new PlatformMaintenance(
            service(),
            EconomyLimits.defaults(),
            () -> root,
            EconomyFixtures.LOG,
            held::add);

        assertTrue(
            maintenance.verify()
                .successful(),
            "первая сверка началась");
        assertEquals(
            ResultCode.BUSY,
            maintenance.verify()
                .code()
                .get(),
            "вторая, пока первая не кончилась, не начинается");
        held.forEach(Runnable::run);

        assertTrue(
            maintenance.verify()
                .successful(),
            "после окончания сверки новая начинается");
    }
}
