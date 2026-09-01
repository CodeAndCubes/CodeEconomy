package com.mrleonardos.codeeconomy.internal;

import com.mrleonardos.codecore.api.config.Comment;
import com.mrleonardos.codeeconomy.api.CurrencyIds;

/**
 * Секция {@code [economy]} главного файла {@code config/code/config.toml}.
 *
 * <p>
 * Сюда уехало то, что админ крутит в первый день: валюта по умолчанию, границы одного перевода и пауза
 * между переводами. В {@code economy.toml} этих ключей нет, иначе одно и то же число спрашивалось бы из
 * двух файлов.
 */
@Comment("Деньги. Файлы лежат в config/code/economy/.")
public final class EconomySection {

    public static final long DEFAULT_MIN_TRANSFER = 1L;
    public static final long DEFAULT_MAX_TRANSFER = 1000000000L;
    public static final int DEFAULT_PAY_COOLDOWN_SECONDS = 0;

    @Comment("Валюта, которую подставляют команды без явного имени.")
    public String defaultCurrency = CurrencyIds.DEFAULT;

    @Comment("Границы одного перевода в минорных единицах.")
    public long minTransfer = DEFAULT_MIN_TRANSFER;

    public long maxTransfer = DEFAULT_MAX_TRANSFER;

    @Comment("Пауза между переводами одного игрока. Ноль снимает.")
    public int payCooldownSeconds = DEFAULT_PAY_COOLDOWN_SECONDS;

    /** Валюта по умолчанию в каноничном виде. Незнакомое значение уходит к заводской. */
    public String currencyId() {
        String normalized = CurrencyIds.normalize(defaultCurrency);
        return CurrencyIds.isValid(normalized) ? normalized : CurrencyIds.DEFAULT;
    }
}
