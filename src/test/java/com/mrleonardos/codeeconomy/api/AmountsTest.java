package com.mrleonardos.codeeconomy.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;

class AmountsTest {

    private static final CurrencyRecord COIN = CurrencyRecord.defaultCoin();

    @Test
    void parseTurnsMinorUnitsOutOfDecimals() {
        assertEquals(1250L, Amounts.parse("12.50", COIN));
        assertEquals(1250L, Amounts.parse("12,5", COIN));
        assertEquals(5L, Amounts.parse("0.05", COIN));
        assertEquals(100000L, Amounts.parse("1000", COIN));
        assertEquals(7L, Amounts.parse("7", coin(0)));
        assertEquals(100L, Amounts.parse("1", COIN));
    }

    @Test
    void parseUnderstandsLeadingMinusAndZero() {
        assertEquals(-1500L, Amounts.parse("-15.00", COIN));
        assertEquals(-5L, Amounts.parse("-0.05", COIN));
        assertEquals(0L, Amounts.parse("0", COIN));
        assertEquals(0L, Amounts.parse("0.00", COIN));
        assertEquals(0L, Amounts.parse("-0", COIN));
    }

    @Test
    void parseTrimsOuterSpaces() {
        assertEquals(1250L, Amounts.parse("  12.50  ", COIN));
    }

    @Test
    void extraFractionDigitIsRefusedWithoutRounding() {
        assertExtraDigit("12.505");
        assertExtraDigit("12.5050");
        assertExtraDigit("1.5", coin(0));
        assertExtraDigit("0.001");
    }

    @Test
    void brokenInputIsRefused() {
        assertBroken("");
        assertBroken("   ");
        assertBroken("-");
        assertBroken(".");
        assertBroken("12..5");
        assertBroken("12.");
        assertBroken("1.2.3");
        assertBroken("1 000");
        assertBroken("12.5a");
        assertBroken("+12.50");
        assertThrows(NullPointerException.class, () -> Amounts.parse(null, COIN));
        assertThrows(NullPointerException.class, () -> Amounts.parse("1", null));
    }

    @Test
    void amountsBeyondLongAreRefusedInsteadOfWrapping() {
        assertBroken("9223372036854775808");
        assertBroken("92233720368547758.08");
        assertBroken("-9223372036854775808");
        assertBroken("99999999999999999999999999");
        assertEquals(9223372036854775807L, Amounts.parse("92233720368547758.07", COIN));
    }

    @Test
    void majorUnitsTravelBothWays() {
        assertEquals(250000L, Amounts.fromMajor(2500L, COIN));
        assertEquals(-200L, Amounts.fromMajor(-2L, COIN));
        assertEquals(9223372036854775800L, Amounts.fromMajor(92233720368547758L, COIN));
        assertEquals(2500L, Amounts.toMajor(250050L, COIN));
        assertEquals(-25L, Amounts.toMajor(-2500L, COIN));
        assertEquals(0L, Amounts.toMajor(1L, COIN));
        assertThrows(IllegalArgumentException.class, () -> Amounts.fromMajor(92233720368547759L, COIN));
    }

    @Test
    void formatFollowsTheCurrencyTemplate() {
        CurrencyRecord credits = CurrencyRecord.builder("credits")
            .displayName("Credits")
            .symbol("cr")
            .decimals(0)
            .maxBalance(1000L)
            .format("%symbol%%amount%")
            .build();
        assertEquals("12.50 $", Amounts.format(1250L, COIN));
        assertEquals("-0.05 $", Amounts.format(-5L, COIN));
        assertEquals("0 $", Amounts.format(0L, coin(0)));
        assertEquals("cr500", Amounts.format(500L, credits));
    }

    @Test
    void formatAmountPadsTheFractionAndKeepsTheSign() {
        assertEquals("12.50", Amounts.formatAmount(1250L, 2));
        assertEquals("125.0", Amounts.formatAmount(1250L, 1));
        assertEquals("1250", Amounts.formatAmount(1250L, 0));
        assertEquals("12.05", Amounts.formatAmount(1205L, 2));
        assertEquals("-0.05", Amounts.formatAmount(-5L, 2));
        assertEquals("-92233720368547758.08", Amounts.formatAmount(Long.MIN_VALUE, 2));
    }

    @Test
    void decimalsOutsideTheModelAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> Amounts.formatAmount(1L, 5));
        assertThrows(IllegalArgumentException.class, () -> Amounts.formatAmount(1L, -1));
    }

    private static void assertExtraDigit(String input) {
        IllegalArgumentException thrown = assertThrows(
            IllegalArgumentException.class,
            () -> Amounts.parse(input, COIN));
        assertTrue(
            thrown.getMessage()
                .contains("digits"),
            () -> "ожидался отказ по лишним знакам, вышло " + thrown.getMessage());
    }

    private static void assertExtraDigit(String input, CurrencyRecord currency) {
        assertThrows(IllegalArgumentException.class, () -> Amounts.parse(input, currency));
    }

    private static void assertBroken(String input) {
        assertThrows(IllegalArgumentException.class, () -> Amounts.parse(input, COIN));
    }

    private static CurrencyRecord coin(int decimals) {
        if (COIN.decimals() == decimals) {
            return COIN;
        }
        return CurrencyRecord.builder(COIN.id())
            .displayName(COIN.displayName())
            .symbol(COIN.symbol())
            .decimals(decimals)
            .startBalance(COIN.startBalance())
            .minBalance(COIN.minBalance())
            .negativeFloor(COIN.negativeFloor())
            .maxBalance(COIN.maxBalance())
            .payAllowed(COIN.payAllowed())
            .visible(COIN.visible())
            .format(COIN.format())
            .build();
    }
}
