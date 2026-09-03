package com.mrleonardos.codeeconomy.platform;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.mrleonardos.codecore.api.actor.PlayerRef;
import com.mrleonardos.codecore.api.command.CommandContext;
import com.mrleonardos.codecore.api.service.PermissionService;
import com.mrleonardos.codeeconomy.internal.Lazy;
import com.mrleonardos.codeeconomy.internal.command.EconomySubjects;

/**
 * Отправитель команды: кто это и что ему можно.
 *
 * <p>
 * Права берутся из реестра ядра по первому вопросу, а не в init. Реестр замерзает в постинициализации,
 * и CodePerms регистрирует свою реализацию в своём init: заберём её раньше, и на сервере с CodePerms
 * половина проверок ушла бы во встроенные json-группы ядра, а другая половина, которую ядро проверяет
 * поздним связыванием, в CodePerms. Одна команда спрашивала бы две разные реализации.
 */
final class SenderSubjects implements EconomySubjects {

    private final Lazy<PermissionService> permissions;
    private final NameResolver names;

    SenderSubjects(Supplier<PermissionService> permissions, NameResolver names) {
        this.permissions = Lazy.of(permissions);
        this.names = names;
    }

    @Override
    public Optional<UUID> subjectOf(CommandContext context) {
        return context.caller()
            .player()
            .map(PlayerRef::id);
    }

    @Override
    public Optional<String> playerName(UUID player) {
        return names.name(player);
    }

    @Override
    public boolean senderHas(CommandContext context, String node) {
        return permissions.get()
            .has(context.caller(), node);
    }
}
