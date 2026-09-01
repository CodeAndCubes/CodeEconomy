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
    private final boolean voided;

    private StoreVerification(List<String> findings, long settled, long ahead, long damaged, boolean voided) {
        this.findings = Collections.unmodifiableList(new ArrayList<>(findings));
        this.settled = settled;
        this.ahead = ahead;
        this.damaged = damaged;
        this.voided = voided;
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

    /**
     * Сверка не состоялась: носитель не читается или сверять его нечем.
     *
     * <p>
     * Отвечать пустым списком расхождений в таком случае нельзя: администратор прочтёт это как «всё
     * сошлось» и уйдёт с уверенностью, которой сверка не давала.
     *
     * @param whyNot что помешало, попадает в отчёт
     */
    public static StoreVerification voided(String whyNot) {
        Objects.requireNonNull(whyNot, "whyNot");
        return new StoreVerification(Collections.singletonList(whyNot), 0L, 0L, 0L, true);
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

    /** Правда ли сверка не состоялась: тогда пустой список расхождений ничего не доказывает. */
    public boolean voided() {
        return voided;
    }

    @Override
    public String toString() {
        return findings.size() + " finding(s), " + settled + " settled, " + ahead + " ahead, " + damaged + " damaged";
    }
}
