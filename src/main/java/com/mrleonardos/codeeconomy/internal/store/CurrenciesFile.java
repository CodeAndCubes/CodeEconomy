package com.mrleonardos.codeeconomy.internal.store;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mrleonardos.codecore.api.config.Comment;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;

/** Содержимое {@code config/code/economy/economy-currencies.toml}. */
@Comment({ "Валюты сервера. Секция на валюту, порядок секций это порядок показа в командах.",
    "Настройки самого мода лежат по соседству, в economy.toml." })
public final class CurrenciesFile {

    @Comment({ "Имя секции это идентификатор валюты, [a-z0-9_] до 16 знаков.",
        "Валюта без maxBalance пропускается с записью в лог, остальные работают." })
    public Map<String, CurrencyEntry> currencies = new LinkedHashMap<>();

    /** Заготовка для нового файла: одна валюта {@code coin}. */
    public static CurrenciesFile defaults() {
        CurrenciesFile file = new CurrenciesFile();
        CurrencyRecord coin = CurrencyRecord.defaultCoin();
        file.currencies.put(coin.id(), CurrencyEntry.of(coin));
        return file;
    }

    /** Файл из готовых валют: выгрузка для тестов и для будущего экспорта. */
    public static CurrenciesFile of(Iterable<CurrencyRecord> records) {
        CurrenciesFile file = new CurrenciesFile();
        for (CurrencyRecord record : records) {
            file.currencies.put(record.id(), CurrencyEntry.of(record));
        }
        return file;
    }

    /** Записи в том порядке, в каком они лежат в файле. */
    public Map<String, CurrencyEntry> entries() {
        return currencies == null ? Collections.<String, CurrencyEntry>emptyMap() : currencies;
    }
}
