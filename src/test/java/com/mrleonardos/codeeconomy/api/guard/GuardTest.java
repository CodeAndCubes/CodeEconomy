package com.mrleonardos.codeeconomy.api.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class GuardTest {

    @Test
    void allowCarriesNoReason() {
        GuardResult allow = GuardResult.allow();
        assertTrue(allow.allowed());
        assertEquals(Optional.empty(), allow.reason());
        assertEquals(allow, GuardResult.allow());
        assertEquals("allow", allow.toString());
    }

    @Test
    void denySpeaksWithAReason() {
        GuardResult denied = GuardResult.deny("перевод крупнее личного потолка");
        assertFalse(denied.allowed());
        assertEquals(Optional.of("перевод крупнее личного потолка"), denied.reason());
        assertEquals("deny: перевод крупнее личного потолка", denied.toString());
        assertFalse(
            GuardResult.deny()
                .reason()
                .isPresent());
        assertEquals(denied, GuardResult.deny("перевод крупнее личного потолка"));
        assertThrows(NullPointerException.class, () -> GuardResult.deny(null));
    }

    @Test
    void guardDescribesItself() {
        TransferGuard guard = new FixedPriorityGuard("paylimit", 10);
        assertEquals("paylimit", guard.id());
        assertEquals(10, guard.priority());
        assertTrue(
            guard.check(null, null, null)
                .allowed());
    }

    private static final class FixedPriorityGuard implements TransferGuard {

        private final String id;

        private final int priority;

        FixedPriorityGuard(String id, int priority) {
            this.id = id;
            this.priority = priority;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public GuardResult check(com.mrleonardos.codeeconomy.api.model.TransferRequest request,
            com.mrleonardos.codeeconomy.api.model.AccountView from,
            com.mrleonardos.codeeconomy.api.model.AccountView to) {
            return GuardResult.allow();
        }
    }
}
