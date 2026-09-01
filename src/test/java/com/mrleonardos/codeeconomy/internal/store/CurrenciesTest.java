package com.mrleonardos.codeeconomy.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codeeconomy.api.CurrencyIds;
import com.mrleonardos.codeeconomy.api.EconomyLimits;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.internal.EconomyFixtures;
import com.mrleonardos.codeeconomy.internal.RecordingLogger;
import com.mrleonardos.codeeconomy.internal.TestConfigs;

class CurrenciesTest {

    private static final String COIN = CurrencyIds.DEFAULT;

    private static final String HUMAN_COMMENT = "# копим на рынок, не трогать";

    @TempDir
    Path root;

    @Test
    void defaultsCarryOneCoin() {
        List<CurrencyRecord> loaded = load(CurrenciesFile.defaults());

        assertEquals(1, loaded.size());
        assertEquals(
            COIN,
            loaded.get(0)
                .id());
        assertEquals(
            25000L,
            loaded.get(0)
                .startBalance());
        assertEquals(
            1000000000000L,
            loaded.get(0)
                .maxBalance());
        assertEquals(
            "%amount% %symbol%",
            loaded.get(0)
                .format());
    }

    /**
     * Порядок валют в файле это порядок показа: мод его не пересортировывает и не теряет между чтением
     * и записью. Сторож сразу против двух откатов: возврата обхода, который сортировал валюты сам, и
     * версии night-config в ядре ниже 3.8, на которой разбор заводил секции неупорядоченными.
     */
    @Test
    void theOrderOfTheFileSurvivesReadingAndWriting() {
        TestConfigs configs = TestConfigs.of(root);
        List<String> written = Arrays.asList("zeny", "coin", "gem", "alpha", "credit");
        ConfigFile<CurrenciesFile> file = configs.open(Currencies.spec());
        file.get().currencies = entries(written);
        file.save();

        file.reload();
        file.save();
        file.reload();

        assertEquals(
            written,
            new ArrayList<>(
                file.get()
                    .entries()
                    .keySet()));
        assertEquals(written, identifiers(load(file.get())));
        assertEquals(written, sectionsOf(TestConfigs.read(configs.path(Currencies.spec()))));
    }

    /** Заводской файл, как его увидит админ: валюта секцией toml, шапка файла и описание над полями. */
    @Test
    void theWrittenFileIsTomlWithAHeaderAndFieldComments() {
        TestConfigs configs = TestConfigs.of(root);
        configs.open(Currencies.spec());

        String text = TestConfigs.read(configs.path(Currencies.spec()));

        assertTrue(text.contains("[currencies.coin]"), () -> "валюта не легла секцией toml:" + text);
        assertTrue(text.contains("# Валюты сервера. Секция на валюту"), () -> "шапка файла не написана:" + text);
        assertTrue(text.contains("# Знак валюты для показа"), () -> "описание поля не доехало до файла:" + text);
        assertTrue(text.contains("startBalance = 25000"), () -> "значение записано не числом toml:" + text);
    }

    /** Строка, дописанная человеком над секцией валюты, переживает перезапись файла модом. */
    @Test
    void aHumanCommentAboveACurrencySurvivesTheRewrite() {
        TestConfigs configs = TestConfigs.of(root);
        ConfigFile<CurrenciesFile> file = configs.open(Currencies.spec());
        Path path = configs.path(Currencies.spec());
        TestConfigs.write(
            path,
            TestConfigs.read(path)
                .replace("[currencies.coin]", HUMAN_COMMENT + System.lineSeparator() + "[currencies.coin]"));

        file.reload();
        file.save();

        assertTrue(
            TestConfigs.read(path)
                .contains(HUMAN_COMMENT),
            "человеческий комментарий стёрт записью мода: " + TestConfigs.read(path));
    }

    @Test
    void maxBalanceAboveTheCeilingIsCapped() {
        CurrenciesFile file = new CurrenciesFile();
        file.currencies.put(COIN, entry(2, Long.valueOf(Long.MAX_VALUE)));

        List<CurrencyRecord> loaded = load(file);

        assertEquals(1, loaded.size());
        assertEquals(
            EconomyLimits.MAX_BALANCE_CAP,
            loaded.get(0)
                .maxBalance());
    }

    @Test
    void unusableEntriesAreSkipped() {
        CurrenciesFile file = new CurrenciesFile();
        file.currencies.put("Bad Id", entry(2, Long.valueOf(1000L)));
        file.currencies.put(COIN, CurrencyEntry.of(EconomyFixtures.coin()));

        List<CurrencyRecord> loaded = load(file);

        assertEquals(1, loaded.size());
        assertEquals(
            COIN,
            loaded.get(0)
                .id());
    }

    @Test
    void currencyCeilingCutsTheTail() {
        CurrenciesFile file = new CurrenciesFile();
        for (int index = 0; index < EconomyLimits.DEFAULT_CURRENCIES + 5; index++) {
            file.currencies.put("cur" + index, entry(2, Long.valueOf(1000L)));
        }

        List<CurrencyRecord> loaded = load(file);

        assertEquals(EconomyLimits.DEFAULT_CURRENCIES, loaded.size());
    }

    @Test
    void fileWithoutCurrenciesFallsBackToCoin() {
        List<CurrencyRecord> loaded = load(new CurrenciesFile());

        assertEquals(1, loaded.size());
        assertEquals(
            COIN,
            loaded.get(0)
                .id());
    }

    /**
     * Валюта без maxBalance грузилась с потолком ноль: любая операция по ней отвечала ABOVE_CEILING, и
     * по логу понять это было нечем, потому что в лог ничего не попадало.
     */
    @Test
    void currencyWithoutMaxBalanceIsSkippedWithALogLine() {
        CurrenciesFile file = new CurrenciesFile();
        CurrencyEntry credit = entry(0, null);
        credit.startBalance = Long.valueOf(100L);
        file.currencies.put("credit", credit);
        RecordingLogger log = new RecordingLogger();

        List<CurrencyRecord> loaded = Currencies.load(file, ceilings(), log.logger());

        assertEquals(1, loaded.size(), "негодная валюта пропущена, остаётся заводская");
        assertEquals(
            COIN,
            loaded.get(0)
                .id());
        assertTrue(log.anyWarnContains("maxBalance"), "в лог уходит имя ключа, которого не хватило");
    }

    private static List<CurrencyRecord> load(CurrenciesFile file) {
        return Currencies.load(file, ceilings(), EconomyFixtures.LOG);
    }

    private static Map<String, CurrencyEntry> entries(List<String> identifiers) {
        Map<String, CurrencyEntry> written = new LinkedHashMap<>();
        for (String id : identifiers) {
            written.put(id, entry(2, Long.valueOf(1000L)));
        }
        return written;
    }

    /** Имена секций валют в том порядке, в каком они лежат в написанном файле. */
    private static List<String> sectionsOf(String text) {
        List<String> found = new ArrayList<>();
        Matcher headers = Pattern.compile("\\[currencies\\.([a-z0-9_]+)]")
            .matcher(text);
        while (headers.find()) {
            found.add(headers.group(1));
        }
        return found;
    }

    private static List<String> identifiers(List<CurrencyRecord> currencies) {
        List<String> found = new ArrayList<>();
        for (CurrencyRecord currency : currencies) {
            found.add(currency.id());
        }
        return found;
    }

    private static CurrencyEntry entry(int decimals, Long maxBalance) {
        CurrencyEntry entry = new CurrencyEntry();
        entry.decimals = Integer.valueOf(decimals);
        entry.maxBalance = maxBalance;
        return entry;
    }

    private static CurrencyRecord zeny() {
        return CurrencyRecord.builder("zeny")
            .decimals(0)
            .maxBalance(1000L)
            .build();
    }

    private static EconomyLimits ceilings() {
        return EconomyFixtures.settings()
            .ceilings(EconomyFixtures.LOG);
    }
}
