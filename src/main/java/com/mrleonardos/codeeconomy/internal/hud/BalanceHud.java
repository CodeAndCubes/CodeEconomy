package com.mrleonardos.codeeconomy.internal.hud;

import java.util.Optional;
import java.util.UUID;

import com.mrleonardos.codeeconomy.api.EconomyService;
import com.mrleonardos.codeeconomy.api.event.BalanceChange;
import com.mrleonardos.codeeconomy.api.event.EconomyListener;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;

/**
 * Показ баланса на экране со стороны сервера.
 *
 * <p>
 * Всё, что он делает, это чтение: берёт уже записанное число и отдаёт его тому, кто просил. Ни одной
 * операции с деньгами здесь нет и быть не должно.
 *
 * <p>
 * Источник изменений это {@link BalanceChange}, который уже коалесцируется за тик по игроку и валюте.
 * Своего опроса счетов у показа нет.
 */
public final class BalanceHud implements EconomyListener {

    private final EconomyService economy;
    private final BalanceHudSink sink;
    private final BalanceWatchers watchers = new BalanceWatchers();

    public BalanceHud(EconomyService economy, BalanceHudSink sink) {
        this.economy = economy;
        this.sink = sink;
    }

    /**
     * Клиент игрока попросил показ и назвал валюту.
     *
     * <p>
     * Незнакомая и невидимая валюта заменяется валютой сервера по умолчанию: опечатка в клиентском
     * файле не должна оставлять игрока без показа.
     */
    public void watch(UUID player, String requested) {
        String currencyId = resolve(requested);
        watchers.watch(player, currencyId);
        push(player, currencyId, economy.balance(player, currencyId));
    }

    /** Игрок ушёл. */
    public void forget(UUID player) {
        watchers.forget(player);
    }

    @Override
    public void onBalanceChange(BalanceChange event) {
        push(event.player(), event.currencyId(), event.after());
    }

    private void push(UUID player, String currencyId, long amount) {
        if (!watchers.takeUpdate(player, currencyId, amount)) {
            return;
        }
        sink.send(player, currencyId, amount, decimalsOf(currencyId));
    }

    private String resolve(String requested) {
        if (requested == null) {
            return economy.defaultCurrencyId();
        }
        String trimmed = requested.trim();
        if (trimmed.isEmpty()) {
            return economy.defaultCurrencyId();
        }
        Optional<CurrencyRecord> known = economy.currency(trimmed);
        if (known.isPresent() && known.get()
            .visible()) {
            return known.get()
                .id();
        }
        return economy.defaultCurrencyId();
    }

    private int decimalsOf(String currencyId) {
        Optional<CurrencyRecord> currency = economy.currency(currencyId);
        return currency.isPresent() ? currency.get()
            .decimals() : 0;
    }
}
