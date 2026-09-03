package com.mrleonardos.codeeconomy.common;

import net.minecraft.entity.player.EntityPlayerMP;

/**
 * То, что приходящие пакеты делают на сервере.
 *
 * <p>
 * Клиент называет только намерение: за какой валютой он хочет следить. Кто он такой и сколько у него
 * денег, сервер знает сам, поэтому ни имени, ни суммы в просьбе нет.
 */
public interface ServerSink {

    /**
     * Клиент игрока готов показывать баланс на экране и просит слать его по этой валюте.
     *
     * @param currencyId идентификатор валюты, пустая строка означает валюту сервера по умолчанию
     */
    void watch(EntityPlayerMP player, String currencyId);
}
