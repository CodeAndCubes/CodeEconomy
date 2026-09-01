package com.mrleonardos.codeeconomy.internal.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.BalanceEntry;

/**
 * Топ по валюте с кешем.
 *
 * <p>
 * Сортировка всех счетов на каждой просьбе стоит заметно дороже, чем нужно команде, поэтому список
 * пересобирается не чаще {@code top.cacheTicks} тиков. Между пересборками отвечает кеш, страницы
 * нарезаются из него.
 *
 * <p>
 * Счёт без записи по валюте стоит в топе со стартовым балансом: {@code /balance} отвечает по нему
 * ровно так же, и расходиться эти два ответа не должны.
 */
public final class TopIndex {

    private final long cacheTicks;
    private final Map<String, Cached> cache = new HashMap<>();

    public TopIndex(long cacheTicks) {
        this.cacheTicks = Math.max(0L, cacheTicks);
    }

    public List<BalanceEntry> top(String currencyId, long startBalance, Map<UUID, AccountView> accounts, int page,
        int pageSize, long tick) {
        if (page < 0 || pageSize <= 0) {
            return Collections.emptyList();
        }
        List<BalanceEntry> sorted = sorted(currencyId, startBalance, accounts, tick);
        long from = (long) page * (long) pageSize;
        if (from >= sorted.size()) {
            return Collections.emptyList();
        }
        int start = (int) from;
        int to = (int) Math.min(from + pageSize, sorted.size());
        return new ArrayList<>(sorted.subList(start, to));
    }

    private List<BalanceEntry> sorted(String currencyId, long startBalance, Map<UUID, AccountView> accounts,
        long tick) {
        synchronized (this) {
            Cached cached = cache.get(currencyId);
            if (cached != null && tick - cached.tick < cacheTicks) {
                return cached.entries;
            }
        }
        List<BalanceEntry> entries = new ArrayList<>(accounts.size());
        for (AccountView account : accounts.values()) {
            Long amount = account.balances()
                .get(currencyId);
            entries.add(
                BalanceEntry.of(
                    account.uuid(),
                    account.name()
                        .orElse(null),
                    amount == null ? startBalance : amount.longValue()));
        }
        entries.sort(ORDER);
        synchronized (this) {
            cache.put(currencyId, new Cached(entries, tick));
        }
        return entries;
    }

    private static final Comparator<BalanceEntry> ORDER = new Comparator<BalanceEntry>() {

        @Override
        public int compare(BalanceEntry left, BalanceEntry right) {
            if (left.amount() != right.amount()) {
                return left.amount() > right.amount() ? -1 : 1;
            }
            String leftName = left.name()
                .map(TopIndex::lowered)
                .orElse(null);
            String rightName = right.name()
                .map(TopIndex::lowered)
                .orElse(null);
            if (leftName == null && rightName == null) {
                return left.player()
                    .compareTo(right.player());
            }
            if (leftName == null) {
                return 1;
            }
            if (rightName == null) {
                return -1;
            }
            int byName = leftName.compareTo(rightName);
            return byName != 0 ? byName
                : left.player()
                    .compareTo(right.player());
        }
    };

    private static String lowered(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static final class Cached {

        private final List<BalanceEntry> entries;
        private final long tick;

        Cached(List<BalanceEntry> entries, long tick) {
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
            this.tick = tick;
        }
    }
}
