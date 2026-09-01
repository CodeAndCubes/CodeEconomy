package com.mrleonardos.codeeconomy.internal.command;

import java.util.Optional;
import java.util.UUID;

import com.mrleonardos.codecore.api.command.CommandContext;

public interface EconomySubjects {

    Optional<UUID> subjectOf(CommandContext context);

    Optional<String> playerName(UUID player);

    boolean senderHas(CommandContext context, String node);
}
