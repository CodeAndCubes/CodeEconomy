package com.mrleonardos.codeeconomy.internal;

import com.mrleonardos.codecore.api.adapter.RoleCapability;
import com.mrleonardos.codecore.api.adapter.RoleSpec;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codeeconomy.api.EconomyService;

/**
 * Роль денег: её объявляет CodeEconomy, потому что реализация по умолчанию живёт здесь же.
 *
 * <p>
 * Перечень умений полный, а каждая заявка называет своё подмножество. Разница и есть то, что на сервере
 * не работает: мост к чужому моду без журнала не объявляет {@code history}, и команда истории не
 * появляется вовсе.
 */
public final class EconomyRole {

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

    private EconomyRole() {}

    public static RoleSpec spec() {
        return RoleSpec.of(ConfigRoles.ECONOMY)
            .capabilities(BALANCE, TRANSFER, CURRENCIES, TOP, HISTORY)
            .services(EconomyService.class)
            .build();
    }
}
