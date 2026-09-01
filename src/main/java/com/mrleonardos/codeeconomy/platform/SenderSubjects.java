package com.mrleonardos.codeeconomy.platform;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;

import com.mrleonardos.codecore.api.command.CommandContext;
import com.mrleonardos.codecore.api.service.PermissionService;
import com.mrleonardos.codeeconomy.internal.command.EconomySubjects;

final class SenderSubjects implements EconomySubjects {

    private final PermissionService permissions;
    private final NameResolver names;

    SenderSubjects(PermissionService permissions, NameResolver names) {
        this.permissions = permissions;
        this.names = names;
    }

    @Override
    public Optional<UUID> subjectOf(CommandContext context) {
        EntityPlayerMP player = context.player();
        return player == null ? Optional.<UUID>empty() : Optional.of(player.getUniqueID());
    }

    @Override
    public Optional<String> playerName(UUID player) {
        return names.name(player);
    }

    @Override
    public boolean senderHas(CommandContext context, String node) {
        return permissions.has(context.sender(), node);
    }
}
