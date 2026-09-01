package com.mrleonardos.codeeconomy.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeeconomy.api.CurrencyIds;
import com.mrleonardos.codeeconomy.api.EconomyLimits;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.internal.EconomyFixtures;
import com.mrleonardos.codeeconomy.internal.RecordingLogger;

class CurrenciesTest {

    private static final String COIN = CurrencyIds.DEFAULT;

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
     * Порядок секций toml до мода не доезжает: разбор кладёт их в неупорядоченную карту. Поэтому валюта
     * по умолчанию идёт первой, а остальные по идентификатору, и ответ один и тот же от запуска к
     * запуску.
     */
    @Test
    void theDefaultCurrencyGoesFirstAndTheRestByIdentifier() {
        CurrenciesFile file = CurrenciesFile
            .of(Arrays.asList(EconomyFixtures.credit(), EconomyFixtures.coin(), zeny()));

        List<CurrencyRecord> loaded = Currencies.load(file, COIN, ceilings(), EconomyFixtures.LOG);

        assertEquals(
            Arrays.asList(COIN, "credit", "zeny"),
            Arrays.asList(
                loaded.get(0)
                    .id(),
                loaded.get(1)
                    .id(),
                loaded.get(2)
                    .id()));
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

        List<CurrencyRecord> loaded = Currencies.load(file, COIN, ceilings(), log.logger());

        assertEquals(1, loaded.size(), "негодная валюта пропущена, остаётся заводская");
        assertEquals(
            COIN,
            loaded.get(0)
                .id());
        assertTrue(log.anyWarnContains("maxBalance"), "в лог уходит имя ключа, которого не хватило");
    }

    private static List<CurrencyRecord> load(CurrenciesFile file) {
        return Currencies.load(file, COIN, ceilings(), EconomyFixtures.LOG);
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
