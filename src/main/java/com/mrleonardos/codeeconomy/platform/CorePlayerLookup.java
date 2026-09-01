package com.mrleonardos.codeeconomy.platform;

import java.util.Optional;
import java.util.UUID;

import com.mrleonardos.codecore.api.CodeApi;
import com.mrleonardos.codecore.api.service.PermissionService;
import com.mrleonardos.codecore.api.util.PlayerNames;
import com.mrleonardos.codeeconomy.internal.service.PlayerLookup;

final class CorePlayerLookup implements PlayerLookup {

    private volatile PermissionService resolved;

    PermissionService permissions() {
        PermissionService known = resolved;
        if (known == null) {
            known = CodeApi.services()
                .require(PermissionService.class);
            resolved = known;
        }
        return known;
    }

    @Override
    public String name(UUID player) {
        return PlayerNames.byId(player);
    }

    @Override
    public boolean has(UUID player, String node) {
        return permissions().has(player, node);
    }

    @Override
    public Optional<String> meta(UUID player, String key) {
        String value = permissions().meta(player, key, null);
        return value == null ? Optional.<String>empty() : Optional.of(value);
    }
}
