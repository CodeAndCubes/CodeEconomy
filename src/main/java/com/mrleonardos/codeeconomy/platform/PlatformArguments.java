package com.mrleonardos.codeeconomy.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mrleonardos.codecore.api.command.ArgumentType;
import com.mrleonardos.codecore.api.command.CommandInputException;
import com.mrleonardos.codecore.api.command.CommandSender;
import com.mrleonardos.codeeconomy.api.CurrencyIds;
import com.mrleonardos.codeeconomy.api.EconomyService;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.internal.command.EconomyArguments;
import com.mrleonardos.codeeconomy.internal.command.EconomyMessages;

final class PlatformArguments implements EconomyArguments {

    private final NameResolver names;
    private final EconomyService economy;

    PlatformArguments(NameResolver names, EconomyService economy) {
        this.names = names;
        this.economy = economy;
    }

    @Override
    public ArgumentType<UUID> player() {
        return new ArgumentType<UUID>() {

            @Override
            public UUID parse(String raw) {
                UUID player = names.id(raw)
                    .orElse(null);
                if (player == null) {
                    throw new CommandInputException(EconomyMessages.FAILURE_UNKNOWN_PLAYER, raw);
                }
                return player;
            }

            @Override
            public List<String> suggestions(CommandSender sender, String partial) {
                return names.suggest(partial, NameResolver.SUGGESTION_LIMIT);
            }
        };
    }

    @Override
    public ArgumentType<String> currency() {
        return new ArgumentType<String>() {

            @Override
            public String parse(String raw) {
                return CurrencyIds.normalize(raw);
            }

            @Override
            public List<String> suggestions(CommandSender sender, String partial) {
                return NameResolver.startingWith(visibleCurrencies(), partial, NameResolver.SUGGESTION_LIMIT);
            }
        };
    }

    private List<String> visibleCurrencies() {
        List<String> ids = new ArrayList<>();
        for (CurrencyRecord currency : economy.currencies()) {
            if (currency.visible()) {
                ids.add(currency.id());
            }
        }
        return ids;
    }
}
