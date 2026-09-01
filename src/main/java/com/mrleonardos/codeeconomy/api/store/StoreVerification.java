package com.mrleonardos.codeeconomy.api.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Итог сверки счетов с носителем.
 *
 * <p>
 * Сверка идёт по снимку счетов и по границе {@code seq}, снятой вместе с ним. Записи ниже границы
 * чекпоинта уже учтены в чекпоинте, записи выше границы снимка появились пока сверка читала носитель:
 * ни те, ни другие не расхождение, поэтому они считаются отдельно и в отчёт попадают числами.
 */
public final class StoreVerification {

    private final List<String> findings;
    private final long settled;
    private final long ahead;
    private final long damaged;
    private final boolean unreadable;

    private StoreVerification(List<String> findings, long settled, long ahead, long damaged, boolean unreadable) {
        this.findings = Collections.unmodifiableList(new ArrayList<>(findings));
        this.settled = settled;
        this.ahead = ahead;
        this.damaged = damaged;
        this.unreadable = unreadable;
    }

    /**
     * Сверка состоялась.
     *
     * @param findings расхождения, первое из них называет отчёт
     * @param settled  число записей ниже границы чекпоинта
     * @param ahead    число записей свежее снимка, с которым шла сверка
     * @param damaged  число строк, которые не удалось разобрать
     */
    public static StoreVerification of(List<String> findings, long settled, long ahead, long damaged) {
        Objects.requireNonNull(findings, "findings");
        return new StoreVerification(findings, settled, ahead, damaged, false);
    }

    /** Сверка не состоялась: носитель не читается. */
    public static StoreVerification unreadable(String reason) {
        Objects.requireNonNull(reason, "reason");
        return new StoreVerification(Collections.singletonList(reason), 0L, 0L, 0L, true);
    }

    /** Расхождения между носителем и счетами. */
    public List<String> findings() {
        return findings;
    }

    /** Число записей ниже границы чекпоинта: их балансы уже в чекпоинте, сверка их не трогает. */
    public long settled() {
        return settled;
    }

    /** Число записей свежее снимка: они легли на носитель пока шла сверка. */
    public long ahead() {
        return ahead;
    }

    /** Число строк, которые не удалось разобрать: каждая это потерянная запись. */
    public long damaged() {
        return damaged;
    }

    /** Правда ли носитель не читается: тогда сверка ничего не доказывает. */
    public boolean unreadable() {
        return unreadable;
    }

    @Override
    public String toString() {
        return findings.size() + " finding(s), " + settled + " settled, " + ahead + " ahead, " + damaged + " damaged";
    }
}
