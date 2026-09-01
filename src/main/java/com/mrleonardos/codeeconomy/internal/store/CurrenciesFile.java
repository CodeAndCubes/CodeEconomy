package com.mrleonardos.codeeconomy.internal.store;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

import com.mrleonardos.codecore.api.config.Comment;
import com.mrleonardos.codeeconomy.api.CurrencyIds;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;

/** Содержимое {@code config/code/economy/economy-currencies.toml}. */
public final class CurrenciesFile {

    @Comment({ "Валюты сервера. Имя секции это идентификатор валюты, [a-z0-9_] до 16 знаков.",
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

    /** Записи в порядке показа: валюта по умолчанию первой, дальше по идентификатору. */
    public Map<String, CurrencyEntry> ordered(String defaultCurrencyId) {
        Map<String, CurrencyEntry> shown = new LinkedHashMap<>();
        if (currencies == null) {
            return shown;
        }
        String first = CurrencyIds.normalize(defaultCurrencyId);
        CurrencyEntry leading = first == null ? null : currencies.get(first);
        if (leading != null) {
            shown.put(first, leading);
        }
        for (String id : new TreeSet<>(currencies.keySet())) {
            if (!shown.containsKey(id)) {
                shown.put(id, currencies.get(id));
            }
        }
        return shown;
    }
}
