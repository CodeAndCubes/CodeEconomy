package com.mrleonardos.codeeconomy.internal.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codecore.api.adapter.RoleOwnerKind;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codeeconomy.EconomyConstants;
import com.mrleonardos.codeeconomy.api.EconomyService;
import com.mrleonardos.codeeconomy.internal.EconomyFixtures;
import com.mrleonardos.codeeconomy.internal.EconomyRole;
import com.mrleonardos.codeeconomy.internal.TestConfigs;
import com.mrleonardos.codeeconomy.internal.service.LedgerService;

class LedgerAdapterTest {

    @TempDir
    Path root;

    private final AtomicInteger built = new AtomicInteger();

    @Test
    void theOfferNamesTheModAndAllFiveCapabilities() {
        LedgerAdapter adapter = new LedgerAdapter(this::assemble);

        assertEquals(ConfigRoles.ECONOMY, adapter.role());
        assertEquals(EconomyConstants.OWNER, adapter.name());
        assertEquals(RoleOwnerKind.MOD, adapter.kind());
        assertTrue(adapter.available(), "наш мод стоит по определению");
        assertEquals(
            EconomyRole.spec()
                .capabilities(),
            adapter.capabilities());
    }

    /** Проиграв роль, мод ничего не собирает: файлы открывает только сборка. */
    @Test
    void nothingIsAssembledUntilTheRoleIsWon() {
        LedgerAdapter adapter = new LedgerAdapter(this::assemble);

        assertNull(adapter.service());
        assertEquals(0, built.get());
    }

    @Test
    void theWinnerHandsTheSameServiceToTheCoreAndToTheMod() {
        LedgerAdapter adapter = new LedgerAdapter(this::assemble);

        EconomyService given = adapter.create()
            .find(EconomyService.class);

        assertEquals(1, built.get());
        assertSame(adapter.service(), given, "мод и ядро работают с одним и тем же леджером");
    }

    private LedgerService assemble() {
        built.incrementAndGet();
        return EconomyFixtures.service(TestConfigs.of(root), Collections.singletonList(EconomyFixtures.coin()));
    }
}
