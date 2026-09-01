package com.mrleonardos.codeeconomy.internal.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.logging.log4j.Logger;

/**
 * Карантин повреждённого журнала: файл убирается рядом, чтобы администратор разобрался, а мод
 * поднимался дальше.
 *
 * <p>
 * Один перенос файла защищает ровно одну сессию: журнала на месте больше нет, и следующий старт видит
 * здоровый чекпоинт. Поэтому вместе с переносом в состояние мира ложится {@link Mark}: пока признак не
 * снят администратором, мод поднимается только для чтения, сколько бы раз сервер ни перезапускали.
 */
public final class Quarantine {

    public static final String SUFFIX = ".quarantine";

    private Quarantine() {}

    /** Убрать журнал в файл с суффиксом {@code .quarantine}. Цель или пустой ответ, если файл не ушёл. */
    public static Path quarantine(Path journal, Logger log) {
        Path target = journal.resolveSibling(journal.getFileName() + SUFFIX);
        try {
            Files.move(journal, target, StandardCopyOption.REPLACE_EXISTING);
            if (log != null) {
                log.warn("Journal {} is moved to {} and is out of use", journal.getFileName(), target.getFileName());
            }
            return target;
        } catch (IOException failure) {
            if (log != null) {
                log.error("Failed to move damaged journal {}: {}", journal, failure.toString());
            }
            return null;
        }
    }

    /** Признак карантина в состоянии мира: когда случилось и что именно потеряно. */
    public static final class Mark {

        private final long at;
        private final String reason;

        public Mark(long at, String reason) {
            this.at = at;
            this.reason = reason;
        }

        /** Когда носитель ушёл в карантин, epoch millis. */
        public long at() {
            return at;
        }

        /** Что случилось: эта строка уходит администратору и в лог каждого старта. */
        public String reason() {
            return reason;
        }

        @Override
        public String toString() {
            return reason + " (at " + at + ")";
        }
    }
}
