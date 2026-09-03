package com.mrleonardos.codeeconomy.api;

import com.mrleonardos.codecore.api.adapter.RoleCapability;

/**
 * Умения роли {@code economy}: перечень объявляет CodeEconomy, потому что реализация по умолчанию живёт
 * здесь же.
 *
 * <p>
 * Константы лежат в api, а не рядом с объявлением роли, ровно затем, чтобы их было чем назвать снаружи. Чужой мод
 * спрашивает {@code CodeApi.adapters().missing(ConfigRoles.ECONOMY).contains(HISTORY)} и не лезет за
 * строкой во внутренности. По той же причине заявка на роль называет своё подмножество этими же
 * константами, а не повторяет строки: две копии перечня расходятся молча, и опечатка в любой из них делает
 * умение навсегда недостающим.
 */
public final class EconomyCapabilities {

    /** Остаток на счету и проверка, хватает ли денег. */
    public static final RoleCapability BALANCE = RoleCapability.of("balance");

    /** Движение денег: перевод, выдача, снятие, установка, сброс. */
    public static final RoleCapability TRANSFER = RoleCapability.of("transfer");

    /** Несколько валют со своими границами и показом. */
    public static final RoleCapability CURRENCIES = RoleCapability.of("currencies");

    /** Топ балансов по валюте. */
    public static final RoleCapability TOP = RoleCapability.of("top");

    /** Журнал операций игрока. */
    public static final RoleCapability HISTORY = RoleCapability.of("history");

    private EconomyCapabilities() {}
}
