package com.mrleonardos.codeeconomy.internal.store;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mrleonardos.codeeconomy.api.CurrencyIds;
import com.mrleonardos.codeeconomy.api.EconomyLimits;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.internal.EconomyFixtures;

class CurrenciesTest {

    @Test
    void defaultsCarryOneCoin() {
        List<CurrencyRecord> loaded = Currencies.load(Currencies.defaults(), ceilings(), EconomyFixtures.LOG);

        assertEquals(1, loaded.size());
        assertEquals(
            CurrencyIds.DEFAULT,
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

    @Test
    void loadKeepsTheOrderOfTheFile() {
        JsonObject file = file(Currencies.encode(Arrays.asList(EconomyFixtures.coin(), EconomyFixtures.credit())));

        List<CurrencyRecord> loaded = Currencies.load(file, ceilings(), EconomyFixtures.LOG);

        assertEquals(
            "coin",
            loaded.get(0)
                .id());
        assertEquals(
            "credit",
            loaded.get(1)
                .id());
    }

    @Test
    void maxBalanceAboveTheCeilingIsCapped() {
        JsonObject entry = new JsonObject();
        entry.addProperty(Currencies.ID, "coin");
        entry.addProperty(Currencies.DECIMALS, 2);
        entry.addProperty(Currencies.MAX_BALANCE, Long.valueOf(Long.MAX_VALUE));

        JsonArray array = new JsonArray();
        array.add(entry);

        List<CurrencyRecord> loaded = Currencies.load(file(array), ceilings(), EconomyFixtures.LOG);

        assertEquals(1, loaded.size());
        assertEquals(
            EconomyLimits.MAX_BALANCE_CAP,
            loaded.get(0)
                .maxBalance());
    }

    @Test
    void unusableEntriesAreSkipped() {
        JsonArray array = new JsonArray();
        array.add(entryWithId("Bad Id"));
        array.add(
            Currencies.encode(Arrays.asList(EconomyFixtures.coin()))
                .get(0));

        List<CurrencyRecord> loaded = Currencies.load(file(array), ceilings(), EconomyFixtures.LOG);

        assertEquals(1, loaded.size());
        assertEquals(
            CurrencyIds.DEFAULT,
            loaded.get(0)
                .id());
    }

    @Test
    void currencyCeilingCutsTheTail() {
        JsonArray array = new JsonArray();
        for (int index = 0; index < EconomyLimits.DEFAULT_CURRENCIES + 5; index++) {
            array.add(entryWithId("cur" + index));
        }

        List<CurrencyRecord> loaded = Currencies.load(file(array), ceilings(), EconomyFixtures.LOG);

        assertEquals(EconomyLimits.DEFAULT_CURRENCIES, loaded.size());
    }

    @Test
    void fileWithoutCurrenciesFallsBackToCoin() {
        List<CurrencyRecord> loaded = Currencies.load(new JsonObject(), ceilings(), EconomyFixtures.LOG);

        assertEquals(1, loaded.size());
        assertEquals(
            CurrencyIds.DEFAULT,
            loaded.get(0)
                .id());
    }

    private static JsonObject file(JsonArray currencies) {
        JsonObject file = new JsonObject();
        file.add(Currencies.LIST_FIELD, currencies);
        return file;
    }

    private static JsonObject entryWithId(String id) {
        JsonObject entry = new JsonObject();
        entry.addProperty(Currencies.ID, id);
        entry.addProperty(Currencies.DECIMALS, 2);
        entry.addProperty(Currencies.MAX_BALANCE, Long.valueOf(1000L));
        return entry;
    }

    private static EconomyLimits ceilings() {
        return EconomyFixtures.settings()
            .ceilings(EconomyFixtures.LOG);
    }

    /**
     * Валюта без maxBalance грузилась с потолком ноль: любая операция по ней отвечала ABOVE_CEILING, и
     * по логу понять это было нечем, потому что в лог ничего не попадало.
     */
    @Test
    void currencyWithoutMaxBalanceIsSkippedWithALogLine() {
        JsonObject entry = new JsonObject();
        entry.addProperty(Currencies.ID, "credit");
        entry.addProperty(Currencies.DECIMALS, 0);
        entry.addProperty(Currencies.START_BALANCE, Long.valueOf(100L));

        JsonArray list = new JsonArray();
        list.add(entry);
        List<CurrencyRecord> loaded = Currencies.load(file(list), ceilings(), EconomyFixtures.LOG);

        assertEquals(1, loaded.size(), "негодная валюта пропущена, остаётся заводская");
        assertEquals(
            CurrencyIds.DEFAULT,
            loaded.get(0)
                .id());
    }
}
