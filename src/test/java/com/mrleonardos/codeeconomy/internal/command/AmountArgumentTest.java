package com.mrleonardos.codeeconomy.internal.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;

class AmountArgumentTest {

    private static final CurrencyRecord COIN = CurrencyRecord.defaultCoin();

    @Test
    void parsesMajorAndMinorUnits() {
        assertEquals(
            1250L,
            AmountArgument.parse("12.50", COIN)
                .getAsLong());
        assertEquals(
            1250L,
            AmountArgument.parse("12,5", COIN)
                .getAsLong());
        assertEquals(
            1200L,
            AmountArgument.parse("12", COIN)
                .getAsLong());
        assertEquals(
            50L,
            AmountArgument.parse("0.50", COIN)
                .getAsLong());
    }

    @Test
    void keepsNegativeAmountsForSet() {
        assertEquals(
            -500L,
            AmountArgument.parse("-5", COIN)
                .getAsLong());
    }

    @Test
    void rejectsStrangersAndOverflow() {
        assertFalse(
            AmountArgument.parse("12.345", COIN)
                .isPresent(),
            "лишний знак после разделителя");
        assertFalse(
            AmountArgument.parse("12 50", COIN)
                .isPresent(),
            "пробел внутри суммы");
        assertFalse(
            AmountArgument.parse("", COIN)
                .isPresent());
        assertFalse(
            AmountArgument.parse(null, COIN)
                .isPresent());
        assertFalse(
            AmountArgument.parse("abc", COIN)
                .isPresent());
        assertFalse(
            AmountArgument.parse("99999999999999999999", COIN)
                .isPresent(),
            "не влезает в long");
    }

    @Test
    void tokenAboveTheCeilingIsRefusedWithoutParsing() {
        StringBuilder token = new StringBuilder("1");
        for (int index = 0; index < AmountArgument.MAX_INPUT; index++) {
            token.append('0');
        }
        assertFalse(
            AmountArgument.parse(token.toString(), COIN)
                .isPresent());

        assertTrue(
            AmountArgument.parse("12.50", COIN)
                .isPresent());
    }
}
