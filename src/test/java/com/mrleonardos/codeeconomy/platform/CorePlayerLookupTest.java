package com.mrleonardos.codeeconomy.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.adapter.AdapterRegistry;
import com.mrleonardos.codecore.api.adapter.PermissionCapabilities;
import com.mrleonardos.codecore.api.adapter.RoleAdapter;
import com.mrleonardos.codecore.api.adapter.RoleCapability;
import com.mrleonardos.codecore.api.adapter.RoleSpec;
import com.mrleonardos.codecore.api.adapter.RoleStatus;
import com.mrleonardos.codecore.api.service.PermissionService;
import com.mrleonardos.codeeconomy.internal.EconomyNodes;
import com.mrleonardos.codeeconomy.internal.RecordingLogger;

/**
 * Недостающее умение {@code meta} названо вслух, а не подменено пустотой.
 *
 * <p>
 * Пустой ответ на мету значит и «значение не задано», и «спросить не у кого». Пока эти два случая
 * выглядели одинаково, личный потолок перевода из меты молча превращался в заводской, и админ узнавал об
 * этом только по жалобе игрока.
 */
class CorePlayerLookupTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Test
    void withoutTheMetaCapabilityTheCeilingIsNotAskedAtAll() {
        RecordingLogger log = new RecordingLogger();
        CorePlayerLookup lookup = new CorePlayerLookup(RefusingPermissions::new);

        lookup.checkMeta(new StubAdapters(PermissionCapabilities.META), log.logger());

        assertFalse(
            lookup.meta(STEVE, EconomyNodes.META_PAY_LIMIT)
                .isPresent(),
            "умения нет, значит потолок из меты не читается вовсе");
        assertFalse(
            lookup.meta(STEVE, EconomyNodes.META_STARTING)
                .isPresent(),
            "стартовый баланс группы держится на том же умении");
        assertTrue(
            log.anyWarnContains(EconomyNodes.META_PAY_LIMIT),
            "выключенный потолок обязан быть назван в логе одной строкой");
        assertTrue(log.anyWarnContains(EconomyNodes.META_STARTING), "второй ключ меты назван той же строкой");
    }

    @Test
    void withTheMetaCapabilityTheValueComesFromPermissions() {
        RecordingLogger log = new RecordingLogger();
        StubPermissions permissions = new StubPermissions("50");
        CorePlayerLookup lookup = new CorePlayerLookup(() -> permissions);

        lookup.checkMeta(new StubAdapters(), log.logger());

        assertEquals(
            "50",
            lookup.meta(STEVE, EconomyNodes.META_PAY_LIMIT)
                .orElse(null));
        assertFalse(log.anyWarnContains(EconomyNodes.META_PAY_LIMIT), "работающему потолку жаловаться не на что");
    }

    @Test
    void anUnsetValueIsNotTheSameAsAMissingCapability() {
        StubPermissions permissions = new StubPermissions(null);
        CorePlayerLookup lookup = new CorePlayerLookup(() -> permissions);

        lookup.checkMeta(new StubAdapters(), new RecordingLogger().logger());

        assertFalse(
            lookup.meta(STEVE, EconomyNodes.META_PAY_LIMIT)
                .isPresent());
        assertTrue(permissions.asked, "значение не задано, но спросить всё равно было у кого");
    }

    /** Реестр ролей, у которого спрашивают одно: чего владелец прав не умеет. */
    private static final class StubAdapters implements AdapterRegistry {

        private final Set<RoleCapability> missing = new LinkedHashSet<>();

        private StubAdapters(RoleCapability... missing) {
            Collections.addAll(this.missing, missing);
        }

        @Override
        public Set<RoleCapability> missing(String role) {
            return missing;
        }

        @Override
        public void declareRole(RoleSpec spec) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void offer(RoleAdapter adapter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String owner(String role) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> candidates(String role) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> roles() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RoleStatus> statuses() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean decided() {
            return true;
        }
    }

    private static class StubPermissions implements PermissionService {

        private final String value;

        private boolean asked;

        private StubPermissions(String value) {
            this.value = value;
        }

        @Override
        public boolean has(UUID player, String node) {
            return false;
        }

        @Override
        public String group(UUID player) {
            return "";
        }

        @Override
        public String meta(UUID player, String key, String fallback) {
            asked = true;
            return value == null ? fallback : value;
        }
    }

    /** Права, к которым обращаться нельзя: без умения {@code meta} их не должны спрашивать вовсе. */
    private static final class RefusingPermissions extends StubPermissions {

        private RefusingPermissions() {
            super(null);
        }

        @Override
        public String meta(UUID player, String key, String fallback) {
            throw new AssertionError("права спросили, хотя умения meta у владельца роли нет");
        }
    }
}
