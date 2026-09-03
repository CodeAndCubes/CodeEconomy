package com.mrleonardos.codeeconomy.internal.hud;

import java.util.Optional;
import java.util.UUID;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codeeconomy.api.EconomyService;
import com.mrleonardos.codeeconomy.api.event.BalanceChange;
import com.mrleonardos.codeeconomy.api.event.EconomyListener;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.internal.EconomyNodes;
import com.mrleonardos.codeeconomy.internal.service.PlayerLookup;

/**
 * Показ баланса на экране со стороны сервера.
 *
 * <p>
 * Всё, что он делает, это чтение: берёт уже записанное число и отдаёт его тому, кто просил. Ни одной
 * операции с деньгами здесь нет и быть не должно.
 *
 * <p>
 * Показ отвечает на тот же вопрос, что и {@code /balance}, поэтому спрашивает ту же ноду
 * {@code codeeconomy.balance}. Своего права под показ нет: завести его значило бы развести два ответа
 * на один вопрос и однажды пустить в обход того, кому команда отказывает.
 *
 * <p>
 * Источник изменений это {@link BalanceChange}, который уже коалесцируется за тик по игроку и валюте.
 * Своего опроса счетов у показа нет.
 */
public final class BalanceHud implements EconomyListener {

    private final EconomyService economy;
    private final PlayerLookup lookup;
    private final BalanceHudSink sink;
    private final Logger log;
    private final BalanceWatchers watchers = new BalanceWatchers();

    public BalanceHud(EconomyService economy, PlayerLookup lookup, BalanceHudSink sink, Logger log) {
        this.economy = economy;
        this.lookup = lookup;
        this.sink = sink;
        this.log = log;
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

    /**
     * Порядок проверок здесь важен. Право спрашивается до {@link BalanceWatchers#takeUpdate}, потому что
     * тот запоминает сумму как отправленную: спроси мы позже, отказ по праву съел бы число, и после
     * возврата ноды экран остался бы с прежним.
     */
    private void push(UUID player, String currencyId, long amount) {
        if (!currencyId.equals(watchers.currency(player))) {
            return;
        }
        if (!allowed(player)) {
            return;
        }
        if (!watchers.takeUpdate(player, currencyId, amount)) {
            return;
        }
        sink.send(player, currencyId, amount, decimalsOf(currencyId));
    }

    /**
     * Право на каждой отправке, а не один раз на запрос: снятая нода гасит показ без перезахода. Ядро
     * прав может ещё не подняться, и тогда показа нет: молча оставить его значило бы показывать баланс
     * тому, кому команда отказывает.
     */
    private boolean allowed(UUID player) {
        try {
            return lookup.has(player, EconomyNodes.BALANCE);
        } catch (RuntimeException failure) {
            if (log != null) {
                log.warn(
                    "Node {} for {} cannot be checked, the balance display stays off: {}",
                    EconomyNodes.BALANCE,
                    player,
                    failure.toString());
            }
            return false;
        }
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
