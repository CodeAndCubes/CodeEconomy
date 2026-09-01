package com.mrleonardos.codeeconomy.internal.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.logging.log4j.Logger;

/**
 * Карантин повреждённого журнала: файл убирается рядом, чтобы администратор разобрался, а мод
 * поднимался дальше.
 */
public final class Quarantine {

    public static final String SUFFIX = ".quarantine";

    private Quarantine() {}

    /** Убрать журнал в {@code journal.jsonl.quarantine}. Цель или пустой ответ, если файл не ушёл. */
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
}
