package com.mrleonardos.codeeconomy.internal.guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeeconomy.api.guard.GuardResult;
import com.mrleonardos.codeeconomy.api.guard.TransferGuard;
import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.TransferRequest;
import com.mrleonardos.codeeconomy.internal.EconomyFixtures;

class GuardChainTest {

    @Test
    void orderFollowsPriorityThenRegistration() {
        GuardChain chain = GuardChain.of(
            Arrays.asList(guard("late", 100, allow), guard("early", 0, allow), guard("also-early", 0, allow)),
            false,
            EconomyFixtures.LOG);

        assertEquals(Arrays.asList("early", "also-early", "late"), ids(chain));
    }

    @Test
    void firstDenyStopsTheChain() {
        List<String> called = new ArrayList<>();

        GuardChain chain = GuardChain.of(
            Arrays
                .asList(guard("denying", 0, GuardResult.deny("too much"), called), guard("second", 50, allow, called)),
            false,
            EconomyFixtures.LOG);

        java.util.Optional<GuardChain.Veto> veto = chain.check(request(), null, null);

        assertTrue(veto.isPresent());
        assertEquals(
            "denying",
            veto.get()
                .guardId());
        assertEquals(
            "too much",
            veto.get()
                .reason());
        assertEquals(Arrays.asList("denying"), called);
    }

    @Test
    void brokenGuardVetoesWhenFailClosed() {
        GuardChain chain = GuardChain.of(Collections.singletonList(failing("broken")), false, EconomyFixtures.LOG);

        GuardChain.Veto veto = chain.check(request(), null, null)
            .orElseThrow(IllegalStateException::new);

        assertEquals("broken", veto.guardId());
        assertTrue(
            veto.reason()
                .contains("guard is broken"));
    }

    @Test
    void brokenGuardIsSkippedWhenFailOpen() {
        GuardChain chain = GuardChain.of(Collections.singletonList(failing("broken")), true, EconomyFixtures.LOG);

        assertFalse(
            chain.check(request(), null, null)
                .isPresent());
    }

    @Test
    void allowGivesNoVeto() {
        GuardChain chain = GuardChain
            .of(Collections.singletonList(guard("fine", 0, allow)), false, EconomyFixtures.LOG);

        assertFalse(
            chain.check(request(), null, null)
                .isPresent());
    }

    private static final GuardResult allow = GuardResult.allow();

    private static TransferGuard failing(String id) {
        return new TransferGuard() {

            @Override
            public String id() {
                return id;
            }

            @Override
            public int priority() {
                return 0;
            }

            @Override
            public GuardResult check(TransferRequest request, AccountView from, AccountView to) {
                throw new IllegalStateException("guard is broken");
            }
        };
    }

    private static TransferGuard guard(String id, int priority, GuardResult answer) {
        return guard(id, priority, answer, null);
    }

    private static TransferGuard guard(String id, int priority, GuardResult answer, List<String> called) {
        return new TransferGuard() {

            @Override
            public String id() {
                return id;
            }

            @Override
            public int priority() {
                return priority;
            }

            @Override
            public GuardResult check(TransferRequest request, AccountView from, AccountView to) {
                if (called != null) {
                    called.add(id);
                }
                return answer;
            }
        };
    }

    private static TransferRequest request() {
        return EconomyFixtures.transfer(EconomyFixtures.ALICE, EconomyFixtures.BOB, 100L, "tx");
    }

    private static List<String> ids(GuardChain chain) {
        List<String> found = new ArrayList<>();
        for (TransferGuard guard : chain.guards()) {
            found.add(guard.id());
        }
        return found;
    }
}
