package com.mrleonardos.codeeconomy.api.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeeconomy.api.model.TransactionRecord;

class EventTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Test
    void balanceChangeHoldsBeforeAndAfter() {
        BalanceChange change = BalanceChange.of(ALICE, "coin", 1000L, 750L);
        assertEquals(ALICE, change.player());
        assertEquals("coin", change.currencyId());
        assertEquals(1000L, change.before());
        assertEquals(750L, change.after());
        assertEquals(change, BalanceChange.of(ALICE, "coin", 1000L, 750L));
        assertThrows(NullPointerException.class, () -> BalanceChange.of(null, "coin", 1L, 2L));
        assertThrows(NullPointerException.class, () -> BalanceChange.of(ALICE, null, 1L, 2L));
    }

    @Test
    void degradedEventTellsEntryAndExit() {
        DegradedEvent entered = DegradedEvent.of(true, "json", "disk is full");
        assertTrue(entered.degraded());
        assertEquals("json", entered.provider());
        assertEquals(
            "disk is full",
            entered.reason()
                .get());

        DegradedEvent restored = DegradedEvent.of(false, "json", null);
        assertFalse(restored.degraded());
        assertFalse(
            restored.reason()
                .isPresent());
        assertThrows(NullPointerException.class, () -> DegradedEvent.of(true, null, null));
    }

    @Test
    void recoveryEventCountsReplayAndFindings() {
        RecoveryEvent clean = RecoveryEvent.of(40L, 5, new ArrayList<String>());
        assertEquals(40L, clean.checkpointSeq());
        assertEquals(5, clean.replayed());
        assertTrue(
            clean.findings()
                .isEmpty());

        List<String> findings = new ArrayList<>();
        findings.add("seq 42: toAfter 100, счёт 90");
        RecoveryEvent broken = RecoveryEvent.of(10L, 2, findings);
        findings.clear();
        assertEquals(
            1,
            broken.findings()
                .size());
        assertThrows(
            UnsupportedOperationException.class,
            () -> broken.findings()
                .add("ещё"));
        assertThrows(NullPointerException.class, () -> RecoveryEvent.of(1L, 1, Arrays.asList((String) null)));
    }

    @Test
    void listenerMethodsAreOptional() {
        EconomyListener listener = new EconomyListener() {};
        listener.onBalanceChange(BalanceChange.of(ALICE, "coin", 1L, 2L));
        listener.onTransactions(new ArrayList<TransactionRecord>());
        listener.onDegraded(DegradedEvent.of(true, "json", null));
        listener.onRecovery(RecoveryEvent.of(0L, 0, new ArrayList<String>()));
    }
}
