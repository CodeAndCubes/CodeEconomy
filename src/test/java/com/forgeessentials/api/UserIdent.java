package com.forgeessentials.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Подставной {@code UserIdent} ForgeEssentials: сигнатуры сняты с ветки {@code 1.7.10/develop}.
 *
 * <p>
 * Настоящий класс тянет за собой типы Minecraft, поэтому в тестовом classpath лежит его двойник с теми
 * же именем пакета и фабриками. Отражение адаптера находит его так же, как нашло бы настоящий.
 */
public class UserIdent {

    private static final Map<UUID, UserIdent> KNOWN = new LinkedHashMap<>();

    private final UUID uuid;
    private final String username;

    private UserIdent(UUID uuid, String username) {
        this.uuid = uuid;
        this.username = username;
    }

    public static synchronized UserIdent get(UUID uuid, String username) {
        UserIdent known = KNOWN.get(uuid);
        if (known == null) {
            known = new UserIdent(uuid, username);
            KNOWN.put(uuid, known);
        }
        return known;
    }

    public static synchronized UserIdent get(UUID uuid) {
        return get(uuid, null);
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public String toString() {
        return username == null ? uuid.toString() : username;
    }
}
