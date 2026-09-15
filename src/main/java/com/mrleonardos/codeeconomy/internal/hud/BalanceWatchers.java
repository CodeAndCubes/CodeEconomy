package com.mrleonardos.codeeconomy.internal.hud;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Кто просил показывать баланс, по какой валюте и какое число ему уже ушло.
 *
 * <p>
 * Отметка ставится по просьбе клиента, а не по списку модов из рукопожатия: список говорит лишь о том,
 * что jar установлен, а не о том, что показ включён и обработчик работает.
 *
 * <p>
 * Балансы меняются часто, поэтому здесь же живёт вся экономия трафика: игрок без просьбы не получает
 * ничего, чужая валюта пропускается, а сумма, равная отправленной прежде, второй раз не уходит.
 */
public final class BalanceWatchers {

    private final Map<UUID, Watcher> watchers = new HashMap<>();

    /** Игрок просит показывать эту валюту. Прежняя отметка стирается, и следующая сумма уйдёт заново. */
    public void watch(UUID player, String currencyId) {
        watchers.put(player, new Watcher(currencyId));
    }

    /** Игрок ушёл: следов не остаётся, иначе карта растёт по числу игроков за всё время работы сервера. */
    public void forget(UUID player) {
        watchers.remove(player);
    }

    /** Валюта, за которой следит игрок, или {@code null}, если он не просил показа. */
    public String currency(UUID player) {
        Watcher watcher = watchers.get(player);
        return watcher == null ? null : watcher.currencyId;
    }

    /** Кто сейчас просит показ: снимок для периодической перепроверки права. */
    public Set<UUID> players() {
        return new HashSet<>(watchers.keySet());
    }

    /** Забыть отправленную сумму: следующая уйдёт, даже если число не менялось. */
    public void resetSent(UUID player) {
        Watcher watcher = watchers.get(player);
        if (watcher != null) {
            watcher.sent = false;
        }
    }

    /**
     * Правда ли эту сумму надо отправить игроку.
     *
     * <p>
     * Ответ «да» сразу запоминает её как отправленную: развести решение и отметку на два вызова значит
     * однажды забыть второй и слать одно и то же число каждый тик.
     */
    public boolean takeUpdate(UUID player, String currencyId, long amount) {
        Watcher watcher = watchers.get(player);
        if (watcher == null || !watcher.currencyId.equals(currencyId)) {
            return false;
        }
        if (watcher.sent && watcher.amount == amount) {
            return false;
        }
        watcher.sent = true;
        watcher.amount = amount;
        return true;
    }

    private static final class Watcher {

        private final String currencyId;

        private boolean sent;
        private long amount;

        Watcher(String currencyId) {
            this.currencyId = currencyId;
        }
    }
}
