package com.mrleonardos.codeeconomy.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.service.ServicePriority;

class EconomySettingsTest {

    @Test
    void theFactoryWeightIsAddonLikeTheRestOfTheLine() {
        assertEquals(
            ServicePriority.ADDON,
            EconomyFixtures.settings()
                .priority(EconomyFixtures.LOG));
    }

    @Test
    void theWeightIsReadFromTheConfigInAnyCase() {
        EconomySettings config = EconomyFixtures.settings();
        config.service.priority = "override";

        assertEquals(ServicePriority.OVERRIDE, config.priority(EconomyFixtures.LOG));
    }

    /** Опечатка в конфиге не должна ронять экономику: заводской вес и строка в логе. */
    @Test
    void anUnknownWeightFallsBackToTheFactoryOne() {
        EconomySettings config = EconomyFixtures.settings();
        config.service.priority = "highest-of-all";

        assertEquals(ServicePriority.ADDON, config.priority(EconomyFixtures.LOG));
    }

    /**
     * Размер страницы зажимается сверху: страница на размер считается при нарезке, и потолок держит
     * произведение в разумных границах вместе с расчётом в long.
     */
    @Test
    void thePageSizeIsClampedFromBothSides() {
        EconomySettings config = EconomyFixtures.settings();
        assertEquals(EconomySettings.DEFAULT_PAGE_SIZE, config.pageSize());

        config.top.pageSize = 5000;
        assertEquals(EconomySettings.MAX_PAGE_SIZE, config.pageSize());

        config.top.pageSize = 0;
        assertEquals(1, config.pageSize());
    }
}
