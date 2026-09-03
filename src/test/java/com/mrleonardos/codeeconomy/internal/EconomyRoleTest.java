package com.mrleonardos.codeeconomy.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.adapter.RoleCapability;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codeeconomy.api.EconomyCapabilities;

/**
 * Перечень умений роли объявлен один раз.
 *
 * <p>
 * Пока имена лежали в двух местах, копии расходились молча: опечатка оставляла умение навсегда
 * недостающим, а сборка была зелёной. Теперь объявление роли берёт константы из api, и тест держит
 * обратную сторону: константу завели, а в заявку роли положить забыли.
 */
class EconomyRoleTest {

    @Test
    void theRoleDeclaresEveryCapabilityTheApiNames() throws IllegalAccessException {
        Set<RoleCapability> named = constantsOf(EconomyCapabilities.class);

        assertFalse(named.isEmpty(), "умения роли денег обязаны быть перечислены в api");
        assertEquals(
            named,
            EconomyRole.spec()
                .capabilities(),
            "перечень заявки роли и перечень api разошлись");
    }

    @Test
    void theRoleKeepsItsName() {
        assertEquals(
            ConfigRoles.ECONOMY,
            EconomyRole.spec()
                .role());
    }

    private static Set<RoleCapability> constantsOf(Class<?> owner) throws IllegalAccessException {
        Set<RoleCapability> found = new LinkedHashSet<>();
        for (Field field : owner.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers())
                && field.getType() == RoleCapability.class) {
                found.add((RoleCapability) field.get(null));
            }
        }
        return found;
    }
}
