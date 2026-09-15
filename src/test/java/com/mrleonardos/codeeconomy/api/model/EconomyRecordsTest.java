package com.mrleonardos.codeeconomy.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeeconomy.api.CurrencyIds;
import com.mrleonardos.codeeconomy.api.EconomyLimits;

class EconomyRecordsTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void defaultCoinMatchesTheFactoryFile() {
        CurrencyRecord coin = CurrencyRecord.defaultCoin();
        assertEquals(CurrencyIds.DEFAULT, coin.id());
        assertEquals(2, coin.decimals());
        assertEquals(25000L, coin.startBalance());
        assertEquals(0L, coin.minBalance());
        assertEquals(0L, coin.negativeFloor());
        assertEquals(1000000000000L, coin.maxBalance());
        assertTrue(coin.payAllowed());
        assertTrue(coin.visible());
        assertTrue(
            coin.format()
                .contains("%amount%"));
        assertEquals("Coins", coin.displayName());
    }

    @Test
    void currencyKeepsItsBounds() {
        CurrencyRecord debt = CurrencyRecord.builder("debt")
            .decimals(0)
            .startBalance(-100L)
            .minBalance(-100L)
            .negativeFloor(-100L)
            .maxBalance(1000L)
            .payAllowed(false)
            .visible(false)
            .build();
        assertEquals("debt", debt.id());
        assertEquals("debt", debt.displayName(), "пустое название заменяется идентификатором");
        assertFalse(debt.payAllowed());
        assertFalse(debt.visible());
        assertEquals("%amount% %symbol%", debt.format());
    }

    @Test
    void currencyRefusesNonsense() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CurrencyRecord.builder("Coin")
                .build());
        assertThrows(
            IllegalArgumentException.class,
            () -> CurrencyRecord.builder("coin")
                .decimals(5)
                .maxBalance(1L)
                .build());
        assertThrows(
            IllegalArgumentException.class,
            () -> CurrencyRecord.builder("coin")
                .maxBalance(Long.MAX_VALUE)
                .build());
        assertThrows(
            IllegalArgumentException.class,
            () -> CurrencyRecord.builder("coin")
                .maxBalance(-1L)
                .build());
        assertThrows(
            IllegalArgumentException.class,
            () -> CurrencyRecord.builder("coin")
                .maxBalance(100L)
                .minBalance(1000L)
                .build());
        assertThrows(
            IllegalArgumentException.class,
            () -> CurrencyRecord.builder("coin")
                .maxBalance(1000L)
                .minBalance(100L)
                .negativeFloor(500L)
                .build());
        assertThrows(
            IllegalArgumentException.class,
            () -> CurrencyRecord.builder("coin")
                .maxBalance(1000L)
                .startBalance(5000L)
                .build());
        assertThrows(
            IllegalArgumentException.class,
            () -> CurrencyRecord.builder("coin")
                .maxBalance(1000L)
                .format("%symbol%")
                .build());
        assertThrows(
            IllegalArgumentException.class,
            () -> CurrencyRecord.builder("coin")
                .maxBalance(1000L)
                .negativeFloor(1L)
                .build());
    }

    @Test
    void accountViewIsImmutableAndAnswersZeroForUnknownCurrency() {
        Map<String, Long> balances = new LinkedHashMap<>();
        balances.put("coin", 1250L);
        AccountView view = AccountView.of(ALICE, "Alice", balances, false, 1000L);
        balances.put("coin", 9999L);
        assertEquals(1250L, view.balance("coin"));
        assertThrows(
            UnsupportedOperationException.class,
            () -> view.balances()
                .put("coin", 1L));
        assertEquals(0L, view.balance("gems"));
        assertEquals(
            "Alice",
            view.name()
                .get());
        assertFalse(view.frozen());
        assertEquals(1000L, view.createdAt());
        assertTrue(
            AccountView.of(ALICE, null, map("coin", -100L), true, 0L)
                .balance("coin") < 0,
            "отрицательный баланс законен при negativeFloor ниже нуля");
        assertTrue(
            AccountView.of(ALICE, null, map("coin", 1L), true, 0L)
                .frozen());
        assertEquals(
            0L,
            AccountView.of(ALICE, null, map(), false, 0L)
                .createdAt());
    }

    @Test
    void transactionCarriesBothSidesOnTransfer() {
        TransactionRecord record = TransactionRecord.builder(TransactionRecord.Kind.TRANSFER, "coin", "cmd:1")
            .seq(1L)
            .ts(5000L)
            .from(ALICE, 600L)
            .to(BOB, 1400L)
            .cause(ChangeCause.COMMAND)
            .actor(ALICE)
            .reason("payment")
            .build();
        assertEquals(1L, record.seq());
        assertEquals(5000L, record.ts());
        assertEquals(TransactionRecord.CURRENT_VERSION, record.version());
        assertEquals("cmd:1", record.transactionId());
        assertEquals(OptionalLong.of(600L), record.fromAfter());
        assertEquals(OptionalLong.of(1400L), record.toAfter());
        assertEquals(
            BOB,
            record.to()
                .get());
        assertEquals(ChangeCause.COMMAND, record.cause());
        assertTrue(
            record.reason()
                .get()
                .equals("payment"));
    }

    @Test
    void oneSidedKindsLeaveTheOtherSideEmpty() {
        TransactionRecord deposit = deposit();
        assertFalse(
            deposit.from()
                .isPresent());
        assertEquals(OptionalLong.empty(), deposit.fromAfter());
        assertEquals(OptionalLong.of(3000L), deposit.toAfter());

        TransactionRecord withdrawal = TransactionRecord.builder(TransactionRecord.Kind.WITHDRAW, "coin", "cmd:3")
            .seq(3L)
            .ts(5000L)
            .from(BOB, 700L)
            .cause(ChangeCause.API)
            .build();
        assertEquals(
            BOB,
            withdrawal.from()
                .get());
        assertEquals(OptionalLong.empty(), withdrawal.toAfter());

        TransactionRecord reset = TransactionRecord.builder(TransactionRecord.Kind.RESET, "coin", "cmd:4")
            .seq(4L)
            .ts(5000L)
            .to(BOB, 25000L)
            .cause(ChangeCause.COMMAND)
            .build();
        assertFalse(
            reset.from()
                .isPresent());
        assertEquals(
            25000L,
            reset.toAfter()
                .getAsLong());
    }

    @Test
    void transactionRefusesBrokenShapes() {
        assertThrows(
            NullPointerException.class,
            () -> TransactionRecord.builder(null, "coin", "cmd:1")
                .seq(1L)
                .ts(1L)
                .cause(ChangeCause.API)
                .build());
        assertThrows(
            IllegalArgumentException.class,
            () -> TransactionRecord.builder(TransactionRecord.Kind.TRANSFER, "c oin", "cmd:1")
                .seq(1L)
                .ts(1L)
                .cause(ChangeCause.API)
                .build());
        assertThrows(
            IllegalArgumentException.class,
            () -> TransactionRecord.builder(TransactionRecord.Kind.TRANSFER, "coin", "")
                .seq(1L)
                .ts(1L)
                .cause(ChangeCause.API)
                .build());
        assertThrows(
            IllegalArgumentException.class,
            () -> TransactionRecord.builder(TransactionRecord.Kind.TRANSFER, "coin", "cmd:1")
                .ts(1L)
                .cause(ChangeCause.API)
                .build());
        assertThrows(
            IllegalArgumentException.class,
            () -> TransactionRecord.builder(TransactionRecord.Kind.TRANSFER, "coin", "cmd:1")
                .seq(1L)
                .cause(ChangeCause.API)
                .build());
        assertThrows(
            IllegalArgumentException.class,
            () -> TransactionRecord.builder(TransactionRecord.Kind.TRANSFER, "coin", "cmd:1")
                .seq(-1L)
                .ts(1L)
                .cause(ChangeCause.API)
                .build());
        assertThrows(
            NullPointerException.class,
            () -> TransactionRecord.builder(TransactionRecord.Kind.TRANSFER, "coin", "cmd:1")
                .seq(1L)
                .ts(1L)
                .build(),
            "без причины запись не собирается");
        assertThrows(
            IllegalArgumentException.class,
            () -> TransactionRecord.builder(TransactionRecord.Kind.TRANSFER, "coin", "cmd:1")
                .seq(1L)
                .ts(1L)
                .cause(ChangeCause.API)
                .build(),
            "перевод без сторон не собирается");
        assertThrows(
            IllegalArgumentException.class,
            () -> TransactionRecord.builder(TransactionRecord.Kind.DEPOSIT, "coin", "cmd:2")
                .seq(2L)
                .ts(1L)
                .from(ALICE, 1L)
                .cause(ChangeCause.API)
                .build(),
            "выдача не несёт отправителя");
        assertThrows(
            IllegalArgumentException.class,
            () -> TransactionRecord.builder(TransactionRecord.Kind.DEPOSIT, "coin", "cmd:2")
                .seq(2L)
                .ts(1L)
                .cause(ChangeCause.API)
                .build(),
            "выдача без получателя не собирается");
        assertThrows(
            IllegalArgumentException.class,
            () -> TransactionRecord.builder(TransactionRecord.Kind.SET, "coin", "cmd:6")
                .seq(6L)
                .ts(1L)
                .from(ALICE, 1L)
                .cause(ChangeCause.API)
                .build(),
            "установка не несёт отправителя");
        assertThrows(
            IllegalArgumentException.class,
            () -> TransactionRecord.builder(TransactionRecord.Kind.TRANSFER, "coin", "cmd:5")
                .seq(5L)
                .ts(1L)
                .to(BOB, 1L)
                .cause(ChangeCause.API)
                .reason(
                    times(
                        'r',
                        EconomyLimits.defaults()
                            .reasonLength() + 1))
                .build());
    }

    @Test
    void requestsCarryTheRightShapeForEachKind() {
        TransferRequest transfer = TransferRequest.transfer(ALICE, BOB, 1250L, "coin", "cmd:1", ALICE, "за товар");
        assertEquals(TransactionRecord.Kind.TRANSFER, transfer.kind());
        assertEquals(1250L, transfer.amount());
        assertEquals(
            ALICE,
            transfer.from()
                .get());
        assertEquals(
            ALICE,
            transfer.actor()
                .get());

        TransferRequest deposit = TransferRequest.deposit(BOB, 100L, "coin", "cmd:2", null, null);
        assertEquals(TransactionRecord.Kind.DEPOSIT, deposit.kind());
        assertFalse(
            deposit.from()
                .isPresent());
        assertFalse(
            deposit.actor()
                .isPresent());
        assertFalse(
            deposit.reason()
                .isPresent());

        TransferRequest withdrawal = TransferRequest.withdraw(BOB, 100L, "coin", "cmd:3", null, null);
        assertEquals(TransactionRecord.Kind.WITHDRAW, withdrawal.kind());
        assertEquals(
            BOB,
            withdrawal.from()
                .get());

        TransferRequest set = TransferRequest.set(BOB, -100L, "coin", "cmd:4", null, "ниже нуля");
        assertEquals(TransactionRecord.Kind.SET, set.kind());
        assertEquals(-100L, set.amount());

        TransferRequest reset = TransferRequest.reset(BOB, "coin", "cmd:5", null, null);
        assertEquals(TransactionRecord.Kind.RESET, reset.kind());
        assertEquals(0L, reset.amount());
    }

    @Test
    void requestsRefuseBrokenValues() {
        assertThrows(
            NullPointerException.class,
            () -> TransferRequest.transfer(null, BOB, 1L, "coin", "cmd:1", null, null));
        assertThrows(
            NullPointerException.class,
            () -> TransferRequest.transfer(ALICE, null, 1L, "coin", "cmd:1", null, null));
        assertThrows(
            IllegalArgumentException.class,
            () -> TransferRequest.transfer(ALICE, BOB, 1L, "c oin", "cmd:1", null, null));
        assertEquals(
            "coin",
            TransferRequest.transfer(ALICE, BOB, 1L, "Coin", "cmd:1", null, null)
                .currencyId(),
            "регистр опускается, а не отклоняется");
        assertThrows(
            NullPointerException.class,
            () -> TransferRequest.transfer(ALICE, BOB, 1L, "coin", null, null, null));
    }

    @Test
    void requestCarriesValuesThePipelineHasToJudge() {
        TransferRequest zero = TransferRequest.transfer(ALICE, BOB, 0L, "coin", "cmd:1", null, null);
        assertEquals(0L, zero.amount(), "нулевую сумму судит конвейер, а не фабрика");

        TransferRequest negative = TransferRequest.deposit(BOB, -1L, "coin", "cmd:2", null, null);
        assertEquals(-1L, negative.amount(), "отрицательную сумму судит конвейер");

        TransferRequest longReason = TransferRequest.transfer(ALICE, BOB, 1L, "coin", "cmd:3", null, times('r', 129));
        assertEquals(
            129,
            longReason.reason()
                .orElse("")
                .length(),
            "длину причины судит конвейер по своим потолкам");

        TransferRequest emptyId = TransferRequest.transfer(ALICE, BOB, 1L, "coin", "", null, null);
        assertEquals("", emptyId.transactionId(), "пустой идентификатор судит конвейер");
    }

    @Test
    void resultSpeaksWithCodesAndBalances() {
        TransferResult ok = TransferResult.success("cmd:1", 600L, 1400L);
        assertEquals(ResultCode.OK, ok.code());
        assertTrue(ok.applied());
        assertEquals(OptionalLong.of(600L), ok.fromAfter());

        TransferResult duplicate = TransferResult.duplicate("cmd:1", 600L, 1400L);
        assertEquals(ResultCode.DUPLICATE, duplicate.code());
        assertTrue(duplicate.applied());
        assertEquals(ok, TransferResult.success("cmd:1", 600L, 1400L));

        TransferResult refused = TransferResult.failure(ResultCode.INSUFFICIENT, "cmd:2", 100L, 5000L);
        assertFalse(refused.applied());
        assertEquals(ResultCode.INSUFFICIENT, refused.code());
        assertEquals(16, ResultCode.values().length);
        assertEquals(
            OptionalLong.empty(),
            TransferResult.failure(ResultCode.UNKNOWN_PLAYER, "cmd:3")
                .toAfter());
        assertThrows(
            IllegalArgumentException.class,
            () -> TransferResult.failure(ResultCode.OK, "cmd:4"),
            "успех приходит только через success");
        assertThrows(IllegalArgumentException.class, () -> TransferResult.failure(ResultCode.DUPLICATE, "cmd:4"));
    }

    @Test
    void balanceEntryHoldsPlayerAndAmount() {
        BalanceEntry entry = BalanceEntry.of(ALICE, "Alice", 1250L);
        assertEquals(ALICE, entry.player());
        assertEquals(
            "Alice",
            entry.name()
                .get());
        assertEquals(1250L, entry.amount());
        assertEquals("Alice=1250", entry.toString());
        assertFalse(
            BalanceEntry.of(ALICE, null, 1L)
                .name()
                .isPresent());
        assertEquals(entry, BalanceEntry.of(ALICE, "Alice", 1250L));
    }

    @Test
    void changeCauseIsFinite() {
        assertEquals(4, ChangeCause.values().length);
        for (ChangeCause cause : ChangeCause.values()) {
            assertEquals(cause, ChangeCause.valueOf(cause.name()));
        }
    }

    private static TransactionRecord deposit() {
        return TransactionRecord.builder(TransactionRecord.Kind.DEPOSIT, "coin", "cmd:2")
            .seq(2L)
            .ts(5000L)
            .to(BOB, 3000L)
            .cause(ChangeCause.IMPORT)
            .build();
    }

    private static Map<String, Long> map(String key, long value) {
        Map<String, Long> balances = new LinkedHashMap<>();
        balances.put(key, value);
        return balances;
    }

    private static Map<String, Long> map() {
        return new LinkedHashMap<>();
    }

    private static String times(char symbol, int count) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < count; index++) {
            text.append(symbol);
        }
        return text.toString();
    }
}
