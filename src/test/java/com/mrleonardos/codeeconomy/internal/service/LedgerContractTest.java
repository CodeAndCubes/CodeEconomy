package com.mrleonardos.codeeconomy.internal.service;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.io.TempDir;

import com.mrleonardos.codecore.api.adapter.RoleCapability;
import com.mrleonardos.codeeconomy.api.EconomyService;
import com.mrleonardos.codeeconomy.api.EconomyServiceContract;
import com.mrleonardos.codeeconomy.api.model.TransferRequest;
import com.mrleonardos.codeeconomy.internal.EconomyFixtures;
import com.mrleonardos.codeeconomy.internal.EconomyRole;
import com.mrleonardos.codeeconomy.internal.TestConfigs;

/** Контракт роли денег на леджере CodeEconomy: он объявил все пять умений и обязан закрыть все проверки. */
class LedgerContractTest extends EconomyServiceContract {

    @TempDir
    Path root;

    private LedgerService service;

    @Override
    protected EconomyService service() {
        if (service == null) {
            service = EconomyFixtures
                .service(TestConfigs.of(root), Arrays.asList(EconomyFixtures.coin(), EconomyFixtures.credit()));
        }
        return service;
    }

    @Override
    protected Set<RoleCapability> capabilities() {
        return EconomyRole.spec()
            .capabilities();
    }

    @Override
    protected void give(UUID player, long amount) {
        service().deposit(
            TransferRequest.deposit(
                player,
                amount,
                service().defaultCurrencyId(),
                "seed:" + player + ":" + amount,
                null,
                "contract"));
    }

    @Override
    protected String secondCurrency() {
        return EconomyFixtures.credit()
            .id();
    }
}
