package com.mrleonardos.codeeconomy.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mrleonardos.codecore.api.adapter.AdapterRegistry;
import com.mrleonardos.codecore.api.adapter.RoleAdapter;
import com.mrleonardos.codecore.api.adapter.RoleCapability;
import com.mrleonardos.codecore.api.adapter.RoleOwnerKind;
import com.mrleonardos.codecore.api.adapter.RoleSpec;
import com.mrleonardos.codecore.api.adapter.RoleStatus;

/**
 * Подставной реестр адаптеров: те же правила выбора владельца, что у ядра, но решение принимает тест.
 *
 * <p>
 * Проверяется здесь не выбор, он живёт и проверяется в ядре, а то, как мод на этот выбор отвечает: сам
 * работает или отходит целиком.
 */
public final class TestAdapters implements AdapterRegistry {

    /** Значение, при котором роль занимает наш мод, если он стоит. */
    public static final String AUTO = "auto";

    /** Значение, при котором роль не занята никем. */
    public static final String OFF = "off";

    private final Map<String, RoleSpec> declared = new LinkedHashMap<>();
    private final List<RoleAdapter> offers = new ArrayList<>();
    private final Map<String, RoleAdapter> owners = new LinkedHashMap<>();

    private boolean decided;

    @Override
    public void declareRole(RoleSpec spec) {
        requireOpen();
        declared.put(spec.role(), spec);
    }

    @Override
    public void offer(RoleAdapter adapter) {
        requireOpen();
        offers.add(adapter);
    }

    /** Решить роль так, как это делает ядро в конце постинициализации, и собрать реализации победителя. */
    public void decide(String role, String requested) {
        RoleAdapter winner = chosen(role, requested);
        if (winner != null) {
            owners.put(role, winner);
            winner.create();
        }
        decided = true;
    }

    @Override
    public String owner(String role) {
        RoleAdapter winner = owners.get(role);
        return winner == null ? null : winner.name();
    }

    @Override
    public List<String> candidates(String role) {
        List<String> names = new ArrayList<>();
        for (RoleAdapter adapter : offers) {
            if (adapter.role()
                .equals(role)) {
                names.add(adapter.name());
            }
        }
        return names;
    }

    @Override
    public Set<RoleCapability> missing(String role) {
        RoleSpec spec = declared.get(role);
        if (spec == null) {
            return Collections.emptySet();
        }
        Set<RoleCapability> missing = new LinkedHashSet<>(spec.capabilities());
        RoleAdapter winner = owners.get(role);
        if (winner != null) {
            missing.removeAll(winner.capabilities());
        }
        return missing;
    }

    @Override
    public List<String> roles() {
        return new ArrayList<>(declared.keySet());
    }

    @Override
    public List<RoleStatus> statuses() {
        return Collections.emptyList();
    }

    @Override
    public boolean decided() {
        return decided;
    }

    /** Заявка на роль по имени: для проверок того, что мод вообще подал. */
    public RoleAdapter offerNamed(String name) {
        for (RoleAdapter adapter : offers) {
            if (adapter.name()
                .equals(name)) {
                return adapter;
            }
        }
        return null;
    }

    private RoleAdapter chosen(String role, String requested) {
        if (OFF.equalsIgnoreCase(requested)) {
            return null;
        }
        if (!AUTO.equalsIgnoreCase(requested)) {
            RoleAdapter named = named(role, requested);
            if (named != null) {
                return named;
            }
        }
        RoleAdapter own = firstOf(role, RoleOwnerKind.MOD);
        return own != null ? own : firstOf(role, RoleOwnerKind.BUILTIN);
    }

    private RoleAdapter named(String role, String requested) {
        for (RoleAdapter adapter : offers) {
            if (adapter.role()
                .equals(role)
                && adapter.name()
                    .equalsIgnoreCase(requested)
                && adapter.available()) {
                return adapter;
            }
        }
        return null;
    }

    private RoleAdapter firstOf(String role, RoleOwnerKind kind) {
        for (RoleAdapter adapter : offers) {
            if (adapter.role()
                .equals(role) && adapter.kind() == kind
                && adapter.available()) {
                return adapter;
            }
        }
        return null;
    }

    private void requireOpen() {
        if (decided) {
            throw new IllegalStateException("Roles are already decided");
        }
    }
}
