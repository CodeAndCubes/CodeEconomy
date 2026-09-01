package com.mrleonardos.codeeconomy.internal.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.BalanceEntry;

class TopIndexTest {

    @Test
    void sortsByAmountThenByNameIgnoringCase() {
        TopIndex top = new TopIndex(100L);

        List<BalanceEntry> entries = top.top("coin", accounts(), 0, 10, 0L);

        assertEquals(Arrays.asList("Zoe", "alice", "alice2", "Alicia", "?", "Bob"), names(entries));
    }

    @Test
    void pagesBeyondTheEdgeGiveNothing() {
        TopIndex top = new TopIndex(100L);

        assertEquals(
            2,
            top.top("coin", accounts(), 0, 2, 0L)
                .size());
        assertEquals(Arrays.asList("Alicia", "?", "Bob"), names(top.top("coin", accounts(), 1, 3, 0L)));
        assertTrue(
            top.top("coin", accounts(), 5, 2, 0L)
                .isEmpty());
        assertTrue(
            top.top("coin", accounts(), 0, 0, 0L)
                .isEmpty());
    }

    @Test
    void cacheIsRebuiltNotMoreOftenThanTheTicksSay() {
        TopIndex top = new TopIndex(100L);
        Map<UUID, AccountView> accounts = new LinkedHashMap<>();
        accounts.put(uuid("a"), account("a", "Alice", 10L));

        assertEquals(Arrays.asList("Alice"), names(top.top("coin", accounts, 0, 10, 0L)));

        accounts.put(uuid("b"), account("b", "Bob", 50L));
        assertEquals(Arrays.asList("Alice"), names(top.top("coin", accounts, 0, 10, 50L)));

        assertEquals(Arrays.asList("Bob", "Alice"), names(top.top("coin", accounts, 0, 10, 100L)));

        top.invalidate();
        assertEquals(Arrays.asList("Bob", "Alice"), names(top.top("coin", accounts, 0, 10, 101L)));
    }

    private static Map<UUID, AccountView> accounts() {
        Map<UUID, AccountView> accounts = new LinkedHashMap<>();
        accounts.put(uuid("a"), account("a", "alice", 100L));
        accounts.put(uuid("b"), account("b", "Bob", 90L));
        accounts.put(uuid("c"), account("c", "Zoe", 300L));
        accounts.put(uuid("d"), account("d", "Alicia", 100L));
        accounts.put(uuid("e"), account("e", null, 100L));
        accounts.put(uuid("f"), account("f", "alice2", 100L));
        return accounts;
    }

    private static AccountView account(String key, String name, long amount) {
        return AccountView
            .of(uuid(key), name, java.util.Collections.singletonMap("coin", Long.valueOf(amount)), false, 0L);
    }

    private static UUID uuid(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes());
    }

    private static List<String> names(List<BalanceEntry> entries) {
        java.util.List<String> found = new java.util.ArrayList<>();
        for (BalanceEntry entry : entries) {
            found.add(
                entry.name()
                    .orElse("?"));
        }
        return found;
    }
}
