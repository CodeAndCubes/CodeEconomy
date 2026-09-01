package com.mrleonardos.codeeconomy.api.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Итог восстановления при старте.
 *
 * <p>
 * Приходит один раз после загрузки: сколько строк журнала переиграно после чекпоинта и что нашлась
 * сверка after-балансов. Пустой список находок значит, что журнал сошёлся с чекпоинтом.
 */
public final class RecoveryEvent {

    private final long checkpointSeq;
    private final int replayed;
    private final List<String> findings;

    private RecoveryEvent(long checkpointSeq, int replayed, List<String> findings) {
        this.checkpointSeq = checkpointSeq;
        this.replayed = replayed;
        this.findings = findings;
    }

    /**
     * Собрать событие.
     *
     * @param findings описания расхождений, например {@code seq 42: toAfter 100, счёт 90}
     */
    public static RecoveryEvent of(long checkpointSeq, int replayed, List<String> findings) {
        Objects.requireNonNull(findings, "findings");
        for (String finding : findings) {
            Objects.requireNonNull(finding, "finding");
        }
        return new RecoveryEvent(checkpointSeq, replayed, Collections.unmodifiableList(new ArrayList<>(findings)));
    }

    /** Наибольший {@code seq}, попавший в чекпоинт. */
    public long checkpointSeq() {
        return checkpointSeq;
    }

    /** Сколько строк журнала переиграно после чекпоинта. */
    public int replayed() {
        return replayed;
    }

    /** Описания найденных расхождений в порядке появления. */
    public List<String> findings() {
        return findings;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RecoveryEvent)) {
            return false;
        }
        RecoveryEvent that = (RecoveryEvent) other;
        return checkpointSeq == that.checkpointSeq && replayed == that.replayed && findings.equals(that.findings);
    }

    @Override
    public int hashCode() {
        return (Long.hashCode(checkpointSeq) * 31 + replayed) * 31 + findings.hashCode();
    }

    @Override
    public String toString() {
        return "checkpoint " + checkpointSeq + ", replayed " + replayed + ", findings " + findings.size();
    }
}
