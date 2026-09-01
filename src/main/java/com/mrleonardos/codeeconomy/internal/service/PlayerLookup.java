package com.mrleonardos.codeeconomy.internal.service;

import java.util.Optional;
import java.util.UUID;

/**
 * Сведения об игроке, которые движок берёт у платформы: ник, право и мета.
 *
 * <p>
 * В {@code internal} нет типов Minecraft, поэтому платформа подставляет реализацию на инициализации:
 * ник из сети и кэша профилей, право и мету из {@code PermissionService} ядра. Консоль платформа
 * обслуживает сама: у неё право есть всегда.
 */
public interface PlayerLookup {

    /**
     * Последний известный ник: сеть, затем кэш профилей.
     *
     * @return ник или null, когда игрок неизвестен
     */
    String name(UUID player);

    /** Правда ли у игрока право, в том числе у оффлайн-игрока. */
    boolean has(UUID player, String node);

    /** Мета игрока или его группы, пустой ответ когда значения нет. */
    Optional<String> meta(UUID player, String key);
}
