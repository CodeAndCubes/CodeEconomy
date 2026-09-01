package com.mrleonardos.codeeconomy.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EconomyLimitsTest {

    @Test
    void factoryValuesMatchTheSpec() {
        EconomyLimits limits = EconomyLimits.defaults();
        assertEquals(64, limits.transactionIdLength());
        assertEquals(128, limits.reasonLength());
        assertEquals(16, limits.currencies());
        assertEquals(200000, limits.accounts());
        assertEquals(10000, limits.historyEntries());
        assertEquals(0, EconomyLimits.MIN_DECIMALS);
        assertEquals(4, EconomyLimits.MAX_DECIMALS);
        assertEquals(4611686018427387903L, EconomyLimits.MAX_BALANCE_CAP);
        assertEquals(4611686018427387903L, Long.MAX_VALUE / 2);
    }

    @Test
    void identifierAndReasonChecks() {
        EconomyLimits limits = EconomyLimits.defaults();
        assertTrue(limits.acceptsTransactionId("cmd:8f2a"));
        assertFalse(limits.acceptsTransactionId(null));
        assertFalse(limits.acceptsTransactionId(""));
        assertFalse(limits.acceptsTransactionId(times('x', 65)));
        assertTrue(limits.acceptsTransactionId(times('x', 64)));
        assertTrue(limits.acceptsReason(times('r', 128)));
        assertFalse(limits.acceptsReason(times('r', 129)));
        assertFalse(limits.acceptsReason(null));
    }

    @Test
    void loweringTakesTheMinimum() {
        EconomyLimits lowered = EconomyLimits.defaults()
            .loweredTo(
                EconomyLimits.builder()
                    .transactionIdLength(16)
                    .reasonLength(32)
                    .currencies(2)
                    .accounts(100)
                    .historyEntries(10)
                    .build());
        assertEquals(16, lowered.transactionIdLength());
        assertEquals(32, lowered.reasonLength());
        assertEquals(2, lowered.currencies());
        assertEquals(100, lowered.accounts());
        assertEquals(10, lowered.historyEntries());
    }

    @Test
    void raisingAboveFactoryValueChangesNothing() {
        EconomyLimits limits = EconomyLimits.defaults();
        EconomyLimits greedy = limits.loweredTo(
            EconomyLimits.builder()
                .transactionIdLength(Integer.MAX_VALUE)
                .reasonLength(Integer.MAX_VALUE)
                .currencies(Integer.MAX_VALUE)
                .accounts(Integer.MAX_VALUE)
                .historyEntries(Integer.MAX_VALUE)
                .build());
        assertEquals(limits, greedy);
        assertEquals(limits.hashCode(), greedy.hashCode());
    }

    @Test
    void balanceCeilingIsCapped() {
        assertEquals(
            4611686018427387903L,
            EconomyLimits.defaults()
                .capMaxBalance(Long.MAX_VALUE));
        assertEquals(
            1000L,
            EconomyLimits.defaults()
                .capMaxBalance(1000L));
        assertEquals(
            0L,
            EconomyLimits.defaults()
                .capMaxBalance(0L));
    }

    @Test
    void decimalsBoundsAreChecked() {
        EconomyLimits limits = EconomyLimits.defaults();
        assertTrue(limits.acceptsDecimals(0));
        assertTrue(limits.acceptsDecimals(4));
        assertFalse(limits.acceptsDecimals(5));
        assertFalse(limits.acceptsDecimals(-1));
    }

    @Test
    void negativeCeilingFallsBackToTheFactoryValue() {
        EconomyLimits lowered = EconomyLimits.builder()
            .currencies(-1)
            .historyEntries(0)
            .build();

        assertEquals(EconomyLimits.defaults(), lowered);
    }

    @Test
    void unusableValuesLeaveARemark() {
        EconomyLimits.Builder builder = EconomyLimits.builder()
            .currencies(-1)
            .accounts(-5)
            .historyEntries(0);

        builder.build();

        assertEquals(
            3,
            builder.remarks()
                .size());
        assertTrue(
            builder.remarks()
                .get(0)
                .contains("currencies"));
        assertTrue(
            builder.remarks()
                .get(2)
                .contains("historyEntries"));
    }

    @Test
    void loweredCeilingsStayBelowTheFactoryOnes() {
        assertFalse(
            EconomyLimits.builder()
                .currencies(4)
                .build()
                .equals(EconomyLimits.defaults()));
    }

    private static String times(char symbol, int count) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < count; index++) {
            text.append(symbol);
        }
        return text.toString();
    }
}
