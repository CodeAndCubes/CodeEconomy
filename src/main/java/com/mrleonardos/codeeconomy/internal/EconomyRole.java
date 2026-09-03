package com.mrleonardos.codeeconomy.internal;

import com.mrleonardos.codecore.api.adapter.RoleSpec;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codeeconomy.api.EconomyCapabilities;
import com.mrleonardos.codeeconomy.api.EconomyService;

/**
 * Роль денег: её объявляет CodeEconomy, потому что реализация по умолчанию живёт здесь же.
 *
 * <p>
 * Перечень умений полный, а каждая заявка называет своё подмножество. Разница и есть то, что на сервере
 * не работает: мост к чужому моду без журнала не объявляет {@code history}, и команда истории не
 * появляется вовсе.
 *
 * <p>
 * Сами имена умений лежат в {@link EconomyCapabilities}: спросить реестр про недостающее умение должен уметь
 * и чужой мод, которому внутренности не видны.
 */
public final class EconomyRole {

    private EconomyRole() {}

    public static RoleSpec spec() {
        return RoleSpec.of(ConfigRoles.ECONOMY)
            .capabilities(
                EconomyCapabilities.BALANCE,
                EconomyCapabilities.TRANSFER,
                EconomyCapabilities.CURRENCIES,
                EconomyCapabilities.TOP,
                EconomyCapabilities.HISTORY)
            .services(EconomyService.class)
            .build();
    }
}
