package com.mrleonardos.codeeconomy.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.adapter.RoleCapability;
import com.mrleonardos.codeeconomy.api.model.BalanceEntry;
import com.mrleonardos.codeeconomy.api.model.ResultCode;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;
import com.mrleonardos.codeeconomy.api.model.TransferRequest;
import com.mrleonardos.codeeconomy.api.model.TransferResult;
import com.mrleonardos.codeeconomy.internal.EconomyFixtures;

/**
 * Один набор проверок на роль денег.
 *
 * <p>
 * Гоняется по леджеру CodeEconomy и по каждому мосту к чужому моду: мост обязан отвечать так же, а не
 * бросать из середины метода. Проверка умения, которого владелец не назвал в своих capabilities,
 * пропускается с отметкой в отчёте, а не считается пройденной.
 *
 * <p>
 * Абсолютных сумм здесь нет: у леджера новый счёт начинается со {@code startBalance} валюты, у чужого
 * мода с нуля, и оба ответа правильные. Проверяется движение денег, а не то, с чего оно началось.
 */
public abstract class EconomyServiceContract {

    protected static final UUID ALICE = EconomyFixtures.ALICE;
    protected static final UUID BOB = EconomyFixtures.BOB;

    private static final String UNKNOWN_CURRENCY = "nosuch";
    private static final String REASON = "contract";

    /** Реализация, которую проверяем. */
    protected abstract EconomyService service();

    /** Что эта реализация про себя объявила. */
    protected abstract Set<RoleCapability> capabilities();

    /** Положить игроку денег средствами своего хранилища. */
    protected abstract void give(UUID player, long amount);

    /** Вторая валюта или {@code null}, если реализация знает только одну. */
    protected String secondCurrency() {
        return null;
    }

    private String currency() {
        return service().defaultCurrencyId();
    }

    @Test
    @DisplayName("валюта по умолчанию известна и лежит в перечне")
    void theDefaultCurrencyIsKnown() {
        assertTrue(
            service().currency(currency())
                .isPresent());
        assertFalse(
            service().currencies()
                .isEmpty());
        assertFalse(
            service().currency(UNKNOWN_CURRENCY)
                .isPresent());
    }

    @Test
    @DisplayName("баланс идёт за деньгами на счету")
    void balanceFollowsTheAccount() {
        assumeSupported(EconomyCapabilities.BALANCE);
        long before = service().balance(ALICE, currency());

        give(ALICE, 500L);

        assertEquals(before + 500L, service().balance(ALICE, currency()));
    }

    @Test
    @DisplayName("хватает ли денег считается по балансу")
    void hasComparesWithTheBalance() {
        assumeSupported(EconomyCapabilities.BALANCE);
        give(ALICE, 500L);
        long balance = service().balance(ALICE, currency());

        assertTrue(service().has(ALICE, balance, currency()));
        assertFalse(service().has(ALICE, balance + 1L, currency()));
    }

    @Test
    @DisplayName("счёт виден после первой мутации")
    void theAccountIsSeenAfterAMutation() {
        assumeSupported(EconomyCapabilities.BALANCE);
        give(BOB, 300L);

        assertTrue(
            service().account(BOB)
                .isPresent());
        assertEquals(
            service().balance(BOB, currency()),
            service().account(BOB)
                .get()
                .balance(currency()));
    }

    @Test
    @DisplayName("незнакомая валюта отвечает отказом, а не пустым балансом")
    void anUnknownCurrencyIsRefused() {
        assumeSupported(EconomyCapabilities.BALANCE);

        TransferResult result = service()
            .deposit(TransferRequest.deposit(ALICE, 100L, UNKNOWN_CURRENCY, "tx:unknown", null, REASON));

        assertEquals(ResultCode.UNKNOWN_CURRENCY, result.code());
        assertEquals(0L, service().balance(ALICE, UNKNOWN_CURRENCY));
    }

    @Test
    @DisplayName("перевод двигает деньги у обеих сторон")
    void aTransferMovesMoneyBothWays() {
        assumeSupported(EconomyCapabilities.TRANSFER);
        give(ALICE, 1000L);
        long from = service().balance(ALICE, currency());
        long to = service().balance(BOB, currency());

        TransferResult result = service()
            .transfer(TransferRequest.transfer(ALICE, BOB, 400L, currency(), "tx:move", ALICE, REASON));

        assertEquals(ResultCode.OK, result.code());
        assertEquals(from - 400L, service().balance(ALICE, currency()));
        assertEquals(to + 400L, service().balance(BOB, currency()));
    }

    @Test
    @DisplayName("перевод больше остатка отклонён и денег не двигает")
    void aTransferAboveTheBalanceIsRefused() {
        assumeSupported(EconomyCapabilities.TRANSFER);
        long from = service().balance(ALICE, currency());
        long to = service().balance(BOB, currency());

        TransferResult result = service()
            .transfer(TransferRequest.transfer(ALICE, BOB, from + 1000L, currency(), "tx:above", ALICE, REASON));

        assertEquals(ResultCode.INSUFFICIENT, result.code());
        assertFalse(result.applied());
        assertEquals(from, service().balance(ALICE, currency()));
        assertEquals(to, service().balance(BOB, currency()));
    }

    @Test
    @DisplayName("перевод самому себе отклонён")
    void aTransferToSelfIsRefused() {
        assumeSupported(EconomyCapabilities.TRANSFER);
        give(ALICE, 500L);

        TransferResult result = service()
            .transfer(TransferRequest.transfer(ALICE, ALICE, 100L, currency(), "tx:self", ALICE, REASON));

        assertEquals(ResultCode.SAME_ACCOUNT, result.code());
    }

    @Test
    @DisplayName("нулевая сумма отклонена")
    void aZeroAmountIsRefused() {
        assumeSupported(EconomyCapabilities.TRANSFER);

        TransferResult result = service()
            .deposit(TransferRequest.deposit(ALICE, 0L, currency(), "tx:zero", null, REASON));

        assertEquals(ResultCode.BAD_AMOUNT, result.code());
    }

    @Test
    @DisplayName("снятие больше остатка отклонено и денег не двигает")
    void aWithdrawAboveTheBalanceIsRefused() {
        assumeSupported(EconomyCapabilities.TRANSFER);
        long before = service().balance(ALICE, currency());

        TransferResult result = service()
            .withdraw(TransferRequest.withdraw(ALICE, before + 1000L, currency(), "tx:take", ALICE, REASON));

        assertFalse(result.applied());
        assertEquals(before, service().balance(ALICE, currency()));
    }

    @Test
    @DisplayName("установка кладёт ровно указанное")
    void setPutsTheExactAmount() {
        assumeSupported(EconomyCapabilities.TRANSFER);

        TransferResult result = service().set(TransferRequest.set(BOB, 777L, currency(), "tx:set", null, REASON));

        assertEquals(ResultCode.OK, result.code());
        assertEquals(777L, service().balance(BOB, currency()));
    }

    @Test
    @DisplayName("повтор операции с тем же идентификатором двигает деньги один раз")
    void aRepeatMovesMoneyOnce() {
        assumeSupported(EconomyCapabilities.HISTORY);
        give(ALICE, 1000L);
        long from = service().balance(ALICE, currency());

        TransferRequest request = TransferRequest.transfer(ALICE, BOB, 200L, currency(), "tx:once", ALICE, REASON);
        assertEquals(
            ResultCode.OK,
            service().transfer(request)
                .code());
        TransferResult repeat = service().transfer(request);

        assertEquals(ResultCode.DUPLICATE, repeat.code());
        assertTrue(repeat.applied());
        assertEquals(from - 200L, service().balance(ALICE, currency()));
    }

    @Test
    @DisplayName("проведённая операция попадает в историю игрока")
    void theOperationIsKeptInHistory() {
        assumeSupported(EconomyCapabilities.HISTORY);
        give(ALICE, 1000L);
        service().transfer(TransferRequest.transfer(ALICE, BOB, 150L, currency(), "tx:history", ALICE, REASON));

        List<TransactionRecord> history = service().history(ALICE, 0, 10);

        assertFalse(history.isEmpty());
        assertEquals(
            "tx:history",
            history.get(0)
                .transactionId());
    }

    @Test
    @DisplayName("топ идёт по убыванию баланса")
    void theTopGoesDown() {
        assumeSupported(EconomyCapabilities.TOP);
        service().set(TransferRequest.set(ALICE, 900L, currency(), "tx:top-a", null, REASON));
        service().set(TransferRequest.set(BOB, 100L, currency(), "tx:top-b", null, REASON));

        List<BalanceEntry> top = service().top(currency(), 0, 10);

        assertEquals(
            ALICE,
            top.get(0)
                .player());
        assertEquals(
            BOB,
            top.get(1)
                .player());
    }

    @Test
    @DisplayName("вторая валюта живёт отдельно от первой")
    void currenciesLiveApart() {
        assumeSupported(EconomyCapabilities.CURRENCIES);
        String second = secondCurrency();
        assumeTrue(second != null, "реализация объявила несколько валют, но второй не назвала");
        long before = service().balance(ALICE, currency());

        TransferResult result = service()
            .deposit(TransferRequest.deposit(ALICE, 50L, second, "tx:second", null, REASON));

        assertEquals(ResultCode.OK, result.code());
        assertEquals(before, service().balance(ALICE, currency()));
    }

    private void assumeSupported(RoleCapability capability) {
        assumeTrue(
            capabilities().contains(capability),
            "умение " + capability + " эта реализация не объявила, проверка пропущена");
    }
}
