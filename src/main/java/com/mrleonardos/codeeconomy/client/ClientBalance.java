package com.mrleonardos.codeeconomy.client;

import com.mrleonardos.codeeconomy.api.EconomyLimits;
import com.mrleonardos.codeeconomy.common.ClientSink;

/**
 * Последнее, что сервер сказал про баланс.
 *
 * <p>
 * Пока пакета не было, показывать нечего: ноль и заглушка врали бы, а игрок принял бы их за настоящий
 * счёт. Число знаков зажимается границами модели: значение приходит по сети, а печать по нему падает на
 * чужом числе.
 */
public final class ClientBalance implements ClientSink {

    private String currencyId = "";
    private long amount;
    private int decimals;
    private boolean known;

    @Override
    public void balance(String currencyId, long amount, int decimals) {
        this.currencyId = currencyId == null ? "" : currencyId;
        this.amount = amount;
        this.decimals = Math.max(EconomyLimits.MIN_DECIMALS, Math.min(EconomyLimits.MAX_DECIMALS, decimals));
        this.known = true;
    }

    /** Сервер снял показ: последнее число больше не показывается как действительное. */
    @Override
    public void hide() {
        known = false;
    }

    /** Игрок вышел из мира: на следующем сервере счёт другой. */
    public void forget() {
        known = false;
    }

    /** Правда ли сервер уже прислал баланс. */
    public boolean known() {
        return known;
    }

    public String currencyId() {
        return currencyId;
    }

    public long amount() {
        return amount;
    }

    public int decimals() {
        return decimals;
    }
}
