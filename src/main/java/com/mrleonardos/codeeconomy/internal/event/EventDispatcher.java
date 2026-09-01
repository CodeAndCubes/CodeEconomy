package com.mrleonardos.codeeconomy.internal.event;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codeeconomy.api.event.BalanceChange;
import com.mrleonardos.codeeconomy.api.event.DegradedEvent;
import com.mrleonardos.codeeconomy.api.event.EconomyEvents;
import com.mrleonardos.codeeconomy.api.event.EconomyListener;
import com.mrleonardos.codeeconomy.api.event.RecoveryEvent;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;

/**
 * Доставка событий слушателям.
 *
 * <p>
 * Порядок задаёт число, при равенстве порядок регистрации. Изменения баланса коалесцируются за тик:
 * пачка операций одного игрока по одной валюте приходит одним событием с итоговыми значениями до и
 * после. Упавший слушатель пишется в лог и пропускается, остальные событие получают.
 */
public final class EventDispatcher implements EconomyEvents {

    private final List<Entry> entries = new CopyOnWriteArrayList<>();
    private final Map<String, Pending> pending = new LinkedHashMap<>();
    private final Logger log;

    public EventDispatcher(Logger log) {
        this.log = log;
    }

    @Override
    public void register(int priority, EconomyListener listener) {
        entries.add(new Entry(priority, entries.size(), listener));
        entries.sort(ORDER);
    }

    @Override
    public void unregister(EconomyListener listener) {
        entries.removeIf(entry -> entry.listener == listener);
    }

    @Override
    public List<EconomyListener> listeners() {
        List<EconomyListener> known = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            known.add(entry.listener);
        }
        return known;
    }

    /** Записи журнала для аудита. */
    public void transactions(List<TransactionRecord> records) {
        for (EconomyListener listener : listeners()) {
            try {
                listener.onTransactions(records);
            } catch (RuntimeException failure) {
                warn(listener, failure);
            }
        }
    }

    /** Итоговое изменение баланса, уходит слушателям на границе тика. */
    public void change(UUID player, String currencyId, long before, long after) {
        if (before == after) {
            return;
        }
        synchronized (pending) {
            pending.merge(key(player, currencyId), new Pending(player, currencyId, before, after), Pending::merge);
        }
    }

    /** Отдать накопленные за тик изменения и сбросить накопитель. */
    public void flushTick() {
        List<Pending> batch;
        synchronized (pending) {
            batch = new ArrayList<>(pending.values());
            pending.clear();
        }
        for (Pending item : batch) {
            BalanceChange event = BalanceChange.of(item.player, item.currencyId, item.before, item.after);
            for (EconomyListener listener : listeners()) {
                try {
                    listener.onBalanceChange(event);
                } catch (RuntimeException failure) {
                    warn(listener, failure);
                }
            }
        }
    }

    /** Вход или выход из режима деградации. */
    public void degraded(DegradedEvent event) {
        for (EconomyListener listener : listeners()) {
            try {
                listener.onDegraded(event);
            } catch (RuntimeException failure) {
                warn(listener, failure);
            }
        }
    }

    /** Итог восстановления при старте. */
    public void recovery(RecoveryEvent event) {
        for (EconomyListener listener : listeners()) {
            try {
                listener.onRecovery(event);
            } catch (RuntimeException failure) {
                warn(listener, failure);
            }
        }
    }

    private void warn(EconomyListener listener, RuntimeException failure) {
        if (log != null) {
            log.warn(
                "Listener {} failed: {}",
                listener.getClass()
                    .getName(),
                failure.toString());
        }
    }

    private static String key(UUID player, String currencyId) {
        return player + "|" + currencyId;
    }

    private static final java.util.Comparator<Entry> ORDER = new java.util.Comparator<Entry>() {

        @Override
        public int compare(Entry left, Entry right) {
            if (left.priority != right.priority) {
                return Integer.compare(left.priority, right.priority);
            }
            return Integer.compare(left.order, right.order);
        }
    };

    private static final class Entry {

        private final int priority;
        private final int order;
        private final EconomyListener listener;

        Entry(int priority, int order, EconomyListener listener) {
            this.priority = priority;
            this.order = order;
            this.listener = listener;
        }
    }

    private static final class Pending {

        private final UUID player;
        private final String currencyId;
        private long before;
        private long after;

        Pending(UUID player, String currencyId, long before, long after) {
            this.player = player;
            this.currencyId = currencyId;
            this.before = before;
            this.after = after;
        }

        static Pending merge(Pending left, Pending right) {
            left.after = right.after;
            return left;
        }
    }
}
