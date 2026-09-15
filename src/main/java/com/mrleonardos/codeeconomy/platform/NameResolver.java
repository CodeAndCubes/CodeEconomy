package com.mrleonardos.codeeconomy.platform;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import com.mrleonardos.codecore.api.actor.PlayerRef;
import com.mrleonardos.codecore.platform.PlayerNames;
import com.mrleonardos.codecore.platform.PlayerRefs;
import com.mrleonardos.codecore.platform.Players;
import com.mrleonardos.codeeconomy.api.model.AccountView;

final class NameResolver {

    /** Наибольшее число подсказок одному запросу: имя одно, место зажима одно. */
    static final int SUGGESTION_LIMIT = 50;

    private final Supplier<Map<UUID, AccountView>> accounts;

    NameResolver(Supplier<Map<UUID, AccountView>> accounts) {
        this.accounts = accounts;
    }

    Optional<String> name(UUID player) {
        String online = PlayerNames.byId(player);
        if (online != null && !online.isEmpty()) {
            return Optional.of(online);
        }
        AccountView account = accounts.get()
            .get(player);
        return account == null ? Optional.<String>empty() : account.name();
    }

    Optional<UUID> id(String input) {
        PlayerRef online = PlayerRefs.of(Players.online(input));
        if (online != null) {
            return Optional.of(online.id());
        }
        for (AccountView account : accounts.get()
            .values()) {
            if (account.name()
                .isPresent()
                && account.name()
                    .get()
                    .equalsIgnoreCase(input)) {
                return Optional.of(account.uuid());
            }
        }
        return Optional.ofNullable(PlayerNames.idByName(input));
    }

    List<String> suggest(String partial, int limit) {
        String prefix = partial.toLowerCase(Locale.ROOT);
        Set<String> candidates = new LinkedHashSet<>(Players.onlineNames());
        for (AccountView account : accounts.get()
            .values()) {
            if (account.name()
                .isPresent()) {
                candidates.add(
                    account.name()
                        .get());
            }
        }
        return startingWith(candidates, prefix, limit);
    }

    /** Кандидаты по префиксу, зажатые общим пределом подсказок. */
    static List<String> startingWith(Iterable<String> candidates, String partial, int limit) {
        List<String> found = new ArrayList<>();
        int cap = Math.min(Math.max(limit, 0), SUGGESTION_LIMIT);
        if (cap == 0) {
            return found;
        }
        String prefix = partial.toLowerCase(Locale.ROOT);
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT)
                .startsWith(prefix)) {
                found.add(candidate);
                if (found.size() == cap) {
                    break;
                }
            }
        }
        return found;
    }
}
