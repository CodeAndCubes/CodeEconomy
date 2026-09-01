package com.mrleonardos.codeeconomy.internal.store;

import com.mrleonardos.codecore.api.config.Comment;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;

/**
 * Валюта в файле {@code economy-currencies.toml}: одна секция на валюту, имя секции это идентификатор.
 *
 * <p>
 * Поля обёрнутые, потому что пропущенный ключ и ключ со значением по умолчанию это разные случаи:
 * валюта без {@code maxBalance} пропускается с записью в лог, а не грузится с нулевым потолком, при
 * котором любая операция отвечает отказом.
 */
public final class CurrencyEntry {

    @Comment("Как валюта называется в сообщениях. Пусто это идентификатор.")
    public String displayName;

    @Comment("Знак валюты для показа. Пусто это идентификатор.")
    public String symbol;

    @Comment({ "Сколько младших знаков видит человек, от 0 до 4.",
        "Внутри валюта всегда целая: два знака означают, что 12.50 это 1250 минорных единиц." })
    public Integer decimals;

    @Comment("Баланс нового счёта в минорных единицах.")
    public Long startBalance;

    @Comment("Пол баланса: ниже него обычная операция не уводит.")
    public Long minBalance;

    @Comment("Дно для операций с нодой codeeconomy.bypass.minbalance. Ноль или отрицательное.")
    public Long negativeFloor;

    @Comment({ "Потолок баланса в минорных единицах, обязателен.", "Выше 4611686018427387903 значение зажимается." })
    public Long maxBalance;

    @Comment("Разрешены ли переводы этой валюты между игроками.")
    public Boolean payAllowed;

    @Comment("Видна ли валюта игроку в командах.")
    public Boolean visible;

    @Comment("Шаблон показа суммы. Обязан нести %amount%, знак валюты подставляет %symbol%.")
    public String format;

    /** Запись файла из готовой валюты: заводское содержимое и выгрузка для тестов. */
    public static CurrencyEntry of(CurrencyRecord currency) {
        CurrencyEntry entry = new CurrencyEntry();
        entry.displayName = currency.displayName();
        entry.symbol = currency.symbol();
        entry.decimals = Integer.valueOf(currency.decimals());
        entry.startBalance = Long.valueOf(currency.startBalance());
        entry.minBalance = Long.valueOf(currency.minBalance());
        entry.negativeFloor = Long.valueOf(currency.negativeFloor());
        entry.maxBalance = Long.valueOf(currency.maxBalance());
        entry.payAllowed = Boolean.valueOf(currency.payAllowed());
        entry.visible = Boolean.valueOf(currency.visible());
        entry.format = currency.format();
        return entry;
    }
}
