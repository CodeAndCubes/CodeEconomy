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

import net.minecraft.entity.player.EntityPlayerMP;

import com.mrleonardos.codecore.api.util.PlayerNames;
import com.mrleonardos.codecore.api.util.Players;
import com.mrleonardos.codeeconomy.api.model.AccountView;

final class NameResolver {

    private static final int SUGGESTION_LIMIT = 50;

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
        EntityPlayerMP online = Players.online(input);
        if (online != null) {
            return Optional.of(online.getUniqueID());
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
        return Optional.<UUID>empty();
    }

    List<String> suggest(String partial, int limit) {
        if (limit <= 0) {
            return new ArrayList<>();
        }
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
        List<String> found = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT)
                .startsWith(prefix)) {
                found.add(candidate);
                if (found.size() == Math.min(limit, SUGGESTION_LIMIT)) {
                    break;
                }
            }
        }
        return found;
    }
}
