package com.mrleonardos.codeeconomy.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CurrencyIdsTest {

    @Test
    void defaultCurrencyIsCoin() {
        assertEquals("coin", CurrencyIds.DEFAULT);
        assertTrue(CurrencyIds.isValid(CurrencyIds.DEFAULT));
    }

    @Test
    void patternHoldsTheLine() {
        assertTrue(CurrencyIds.isValid("credits"));
        assertTrue(CurrencyIds.isValid("a"));
        assertTrue(CurrencyIds.isValid("c0in_1"));
        assertFalse(CurrencyIds.isValid(null));
        assertFalse(CurrencyIds.isValid(""));
        assertFalse(CurrencyIds.isValid("Coin"));
        assertFalse(CurrencyIds.isValid("c oin"));
        assertFalse(CurrencyIds.isValid("копейка"));
        assertFalse(CurrencyIds.isValid(times('c', 17)));
    }

    @Test
    void normalizeTrimsAndLowers() {
        assertEquals("coin", CurrencyIds.normalize(" Coin "));
        assertNull(CurrencyIds.normalize(null));
    }

    @Test
    void checkedSpeaksUpOnGarbage() {
        assertEquals("coin", CurrencyIds.checked(" Coin "));
        assertEquals("coin", CurrencyIds.checked("Coin"), "регистр опускается, а не отклоняется");
        assertThrows(IllegalArgumentException.class, () -> CurrencyIds.checked("c oin"));
        assertThrows(IllegalArgumentException.class, () -> CurrencyIds.checked(""));
    }

    private static String times(char symbol, int count) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < count; index++) {
            text.append(symbol);
        }
        return text.toString();
    }
}
