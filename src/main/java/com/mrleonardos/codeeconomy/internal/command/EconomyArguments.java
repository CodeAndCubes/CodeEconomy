package com.mrleonardos.codeeconomy.internal.command;

import java.util.UUID;

import com.mrleonardos.codecore.api.command.ArgumentType;

public interface EconomyArguments {

    ArgumentType<UUID> player();

    ArgumentType<String> currency();
}
