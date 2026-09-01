package com.mrleonardos.codeeconomy.internal.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeeconomy.api.event.BalanceChange;
import com.mrleonardos.codeeconomy.api.event.DegradedEvent;
import com.mrleonardos.codeeconomy.api.event.EconomyListener;
import com.mrleonardos.codeeconomy.api.event.RecoveryEvent;
import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;
import com.mrleonardos.codeeconomy.internal.EconomyFixtures;

class EventDispatcherTest {

    @Test
    void listenersAreCalledByPriorityThenRegistration() {
        EventDispatcher events = new EventDispatcher(EconomyFixtures.LOG);
        List<String> order = new ArrayList<>();
        events.register(10, listener("late", order));
        events.register(0, listener("first", order));
        events.register(0, listener("second", order));

        events.transactions(Collections.singletonList(record()));

        assertEquals(Arrays.asList("first", "second", "late"), order);
    }

    @Test
    void unregisterRemovesTheListener() {
        EventDispatcher events = new EventDispatcher(EconomyFixtures.LOG);
        List<String> order = new ArrayList<>();
        EconomyListener listener = listener("only", order);
        events.register(0, listener);

        events.unregister(listener);
        events.transactions(Collections.singletonList(record()));

        assertTrue(order.isEmpty());
        assertTrue(
            events.listeners()
                .isEmpty());
    }

    @Test
    void brokenListenerIsSkipped() {
        EventDispatcher events = new EventDispatcher(EconomyFixtures.LOG);
        List<String> order = new ArrayList<>();
        events.register(0, new EconomyListener() {

            @Override
            public void onTransactions(List<TransactionRecord> records) {
                throw new IllegalStateException("listener is broken");
            }
        });
        events.register(5, listener("after", order));

        events.transactions(Collections.singletonList(record()));

        assertEquals(Arrays.asList("after"), order);
    }

    @Test
    void changesOfOneTickAreCoalesced() {
        EventDispatcher events = new EventDispatcher(EconomyFixtures.LOG);
        List<BalanceChange> seen = new ArrayList<>();
        events.register(0, new EconomyListener() {

            @Override
            public void onBalanceChange(BalanceChange event) {
                seen.add(event);
            }
        });

        events.change(EconomyFixtures.ALICE, "coin", 1000L, 900L);
        events.change(EconomyFixtures.ALICE, "coin", 900L, 850L);
        events.change(EconomyFixtures.ALICE, "coin", 850L, 700L);
        events.change(EconomyFixtures.BOB, "coin", 500L, 600L);
        events.flushTick();

        assertEquals(2, seen.size());
        assertEquals(
            1000L,
            seen.get(0)
                .before());
        assertEquals(
            700L,
            seen.get(0)
                .after());
        assertEquals(
            EconomyFixtures.BOB,
            seen.get(1)
                .player());

        events.flushTick();
        assertEquals(2, seen.size());
    }

    @Test
    void degradedAndRecoveryReachListeners() {
        EventDispatcher events = new EventDispatcher(EconomyFixtures.LOG);
        List<String> seen = new ArrayList<>();
        events.register(0, new EconomyListener() {

            @Override
            public void onDegraded(DegradedEvent event) {
                seen.add("degraded " + event.degraded());
            }

            @Override
            public void onRecovery(RecoveryEvent event) {
                seen.add("recovery " + event.checkpointSeq());
            }
        });

        events.degraded(DegradedEvent.of(true, "json", "disk is gone"));
        events.recovery(RecoveryEvent.of(7L, 3, Collections.singletonList("seq 9: mismatch")));

        assertEquals(Arrays.asList("degraded true", "recovery 7"), seen);
    }

    private static EconomyListener listener(String name, List<String> order) {
        return new EconomyListener() {

            @Override
            public void onTransactions(List<TransactionRecord> records) {
                order.add(name);
            }
        };
    }

    private static TransactionRecord record() {
        return TransactionRecord.builder(TransactionRecord.Kind.DEPOSIT, "coin", "tx")
            .seq(1L)
            .ts(1000L)
            .to(EconomyFixtures.ALICE, 1000L)
            .cause(ChangeCause.COMMAND)
            .build();
    }
}
