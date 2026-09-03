package com.mrleonardos.codeeconomy.platform;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.adapter.AdapterRegistry;
import com.mrleonardos.codecore.api.adapter.PermissionCapabilities;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.service.PermissionService;
import com.mrleonardos.codecore.platform.PlayerNames;
import com.mrleonardos.codeeconomy.internal.EconomyNodes;
import com.mrleonardos.codeeconomy.internal.Lazy;
import com.mrleonardos.codeeconomy.internal.service.PlayerLookup;

final class CorePlayerLookup implements PlayerLookup {

    private final Lazy<PermissionService> permissions;

    private volatile boolean metaWorks = true;

    CorePlayerLookup(Supplier<PermissionService> permissions) {
        this.permissions = Lazy.of(permissions);
    }

    /**
     * Умеет ли владелец роли прав отдавать мету. Спрашивается один раз на старте сервера: роли решены в
     * конце постинициализации ядра и дальше не меняются.
     *
     * <p>
     * Умения нет, значит личный потолок перевода не читается вовсе. Иначе пустой ответ значил бы и
     * «значение не задано», и «спросить не у кого», а различить это спрашивающему нечем: потолок из меты
     * молча стал бы заводским.
     */
    void checkMeta(AdapterRegistry adapters, Logger log) {
        metaWorks = !adapters.missing(ConfigRoles.PERMISSIONS)
            .contains(PermissionCapabilities.META);
        if (!metaWorks) {
            log.warn(
                "Owner of role permissions carries no meta, so personal ceiling {} is not read at all: "
                    + "transfers are bounded by [economy] minTransfer and maxTransfer",
                EconomyNodes.META_PAY_LIMIT);
        }
    }

    @Override
    public String name(UUID player) {
        return PlayerNames.byId(player);
    }

    @Override
    public boolean has(UUID player, String node) {
        return permissions.get()
            .has(player, node);
    }

    @Override
    public Optional<String> meta(UUID player, String key) {
        if (!metaWorks) {
            return Optional.empty();
        }
        String value = permissions.get()
            .meta(player, key, null);
        return value == null ? Optional.<String>empty() : Optional.of(value);
    }
}
