package com.mrleonardos.codeeconomy.internal.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.adapter.RoleCapability;
import com.mrleonardos.codecore.api.adapter.RoleOwnerKind;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codeeconomy.api.EconomyCapabilities;
import com.mrleonardos.codeeconomy.api.EconomyService;
import com.mrleonardos.codeeconomy.api.EconomyServiceContract;
import com.mrleonardos.codeeconomy.api.model.ResultCode;
import com.mrleonardos.codeeconomy.api.model.TransferRequest;
import com.mrleonardos.codeeconomy.api.model.TransferResult;
import com.mrleonardos.codeeconomy.internal.EconomyFixtures;
import com.mrleonardos.codeeconomy.internal.EconomyNodes;
import com.mrleonardos.codeeconomy.internal.EconomySection;
import com.mrleonardos.codeeconomy.internal.RecordingLogger;

/**
 * Контракт роли денег на мосте к ForgeEssentials.
 *
 * <p>
 * Чужой api подставлен классами с теми же именами пакетов в тестовом classpath: отражение моста находит
 * их так же, как нашло бы настоящие. Проверки истории, топа и второй валюты пропускаются, потому что
 * этих умений заявка не объявила.
 */
class ForgeEssentialsContractTest extends EconomyServiceContract {

    private final ForgeEssentialsAdapter adapter = new ForgeEssentialsAdapter(
        EconomySection::new,
        EconomyFixtures.lookup(),
        inline(),
        EconomyFixtures.LOG);

    private FakeForgeEssentials foreign;
    private EconomyService service;

    @BeforeEach
    void installForeignMod() {
        foreign = FakeForgeEssentials.install();
        service = adapter.create()
            .find(EconomyService.class);
    }

    @AfterEach
    void removeForeignMod() {
        FakeForgeEssentials.uninstall();
    }

    @Override
    protected EconomyService service() {
        return service;
    }

    @Override
    protected Set<RoleCapability> capabilities() {
        return adapter.capabilities();
    }

    @Override
    protected void give(UUID player, long amount) {
        foreign.put(player, foreign.balanceOf(player) + amount);
    }

    @Test
    void theOfferNamesItselfAndTheRoleItAsksFor() {
        assertEquals("forgeessentials", adapter.name());
        assertEquals(ConfigRoles.ECONOMY, adapter.role());
        assertEquals(RoleOwnerKind.ADAPTER, adapter.kind());
        assertTrue(adapter.available(), "классы чужого api лежат в тестовом classpath");
    }

    @Test
    void theOfferAdmitsWhatItCannotDo() {
        Set<RoleCapability> declared = adapter.capabilities();

        assertTrue(declared.contains(EconomyCapabilities.BALANCE));
        assertTrue(declared.contains(EconomyCapabilities.TRANSFER));
        assertFalse(declared.contains(EconomyCapabilities.CURRENCIES), "валюта у чужого мода одна");
        assertFalse(declared.contains(EconomyCapabilities.HISTORY), "журнала у чужого мода нет");
        assertFalse(declared.contains(EconomyCapabilities.TOP), "перебрать кошельки чужой мод не даёт");
    }

    /** Недоступное умение отвечает пустотой, а не исключением из середины метода. */
    @Test
    void whatTheOfferCannotDoAnswersEmpty() {
        give(EconomyFixtures.ALICE, 500L);

        assertTrue(
            service.history(EconomyFixtures.ALICE, 0, 10)
                .isEmpty());
        assertTrue(
            service.top(service.defaultCurrencyId(), 0, 10)
                .isEmpty());
    }

    /**
     * Чего в перечень умений не уложить, о том мод говорит при выборе владельца: перевод не атомарен, а
     * личный потолок из меты не работает вовсе. Молчаливая потеря личного лимита выглядит как
     * работающая настройка, и это хуже, чем её отсутствие.
     */
    @Test
    void theOwnerIsNamedTogetherWithWhatItSilentlyLoses() {
        RecordingLogger log = new RecordingLogger();

        new ForgeEssentialsAdapter(EconomySection::new, EconomyFixtures.lookup(), inline(), log.logger()).create();

        assertTrue(log.anyWarnContains("without atomicity"), "перевод не атомарен");
        assertTrue(log.anyWarnContains(EconomyNodes.META_PAY_LIMIT), "личный потолок из меты не работает");
    }

    /** Чужой модуль поднимается своим чередом, и до этого момента кошельков нет, а падений тоже нет. */
    @Test
    void anEconomyThatIsNotUpYetAnswersWithoutFalling() {
        FakeForgeEssentials.uninstall();

        assertEquals(0L, service.balance(EconomyFixtures.ALICE, service.defaultCurrencyId()));
        assertFalse(
            service.account(EconomyFixtures.ALICE)
                .isPresent());
    }

    /**
     * Тихий провал add: списание у отправителя прошло, зачисление не подтвердилось. Успех с ложным
     * балансом означал бы исчезнувшие деньги, поэтому перевод отвечает отказом и оставляет строку в
     * логе.
     */
    @Test
    void aSilentlyRefusedAddIsReportedAsFailureNotSuccess() {
        give(EconomyFixtures.ALICE, 1000L);
        give(EconomyFixtures.BOB, 500L);
        foreign.ignoreWrites = true;

        TransferResult result = service.transfer(
            TransferRequest.transfer(
                EconomyFixtures.ALICE,
                EconomyFixtures.BOB,
                200L,
                service.defaultCurrencyId(),
                "tx1",
                null,
                null));

        assertEquals(ResultCode.STORE_FAILURE, result.code(), "тихий провал add не проводится как успех");
        assertEquals(800L, foreign.balanceOf(EconomyFixtures.ALICE), "списание у источника правда прошло");
        assertEquals(500L, foreign.balanceOf(EconomyFixtures.BOB), "зачисление не подтвердилось");
    }

    /** Тихий провал set: установка отвечает отказом, а не успехом с прежним числом. */
    @Test
    void aSilentlyRefusedSetIsReportedAsFailureNotSuccess() {
        give(EconomyFixtures.ALICE, 1000L);
        foreign.ignoreWrites = true;

        TransferResult result = service
            .set(TransferRequest.set(EconomyFixtures.ALICE, 400L, service.defaultCurrencyId(), "tx2", null, null));

        assertEquals(ResultCode.STORE_FAILURE, result.code());
        assertEquals(1000L, foreign.balanceOf(EconomyFixtures.ALICE), "кошелёк не подтвердил установку");
    }

    @Test
    void theSingleCurrencyIsNamedByTheForeignMod() {
        assertEquals(
            FakeForgeEssentials.CURRENCY_NAME,
            service.currency(service.defaultCurrencyId())
                .get()
                .displayName());
        assertEquals(
            1,
            service.currencies()
                .size());
    }

    private static Scheduler inline() {
        return new Scheduler() {

            @Override
            public void onMainThread(Runnable task) {
                task.run();
            }

            @Override
            public void afterTicks(int ticks, Runnable task) {
                task.run();
            }
        };
    }
}
