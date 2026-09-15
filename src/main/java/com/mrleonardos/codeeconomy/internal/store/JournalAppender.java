package com.mrleonardos.codeeconomy.internal.store;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;

final class JournalAppender {

    private static final String LINE_SEPARATOR = "\n";

    private static final String TEMPORARY_SUFFIX = ".tmp";

    private final Path path;
    private FileChannel channel;
    private boolean rollbackFailed;
    private boolean untrusted;

    JournalAppender(Path path) {
        this.path = path;
    }

    Path path() {
        return path;
    }

    /** Правда ли последнему откату не удалось вернуть файл к прежней длине. */
    synchronized boolean rollbackFailed() {
        return rollbackFailed;
    }

    /**
     * Правда ли хвосту журнала можно доверять. Откат, которому изменила файловая система, оставляет
     * после последней целой строки обрывок: следующая запись легла бы за ним и при разборе выглядела
     * бы битой серединой. Пока флаг стоит, запись запрещена до полной перезаписи файла.
     */
    synchronized boolean trusted() {
        return !untrusted;
    }

    /**
     * Дописать порцию байтов одной записью с принудительным сбросом. Отказ возвращается вызывающему
     * только после отката файла к прежней длине, поэтому хвост журнала никогда не содержит обрывка
     * недописанной строки, а следующая запись ложится на чистую границу.
     */
    synchronized void append(String line) throws IOException {
        FileChannel target = channel();
        long before = target.size();
        rollbackFailed = false;
        try {
            ByteBuffer source = ByteBuffer.wrap((line + LINE_SEPARATOR).getBytes(StandardCharsets.UTF_8));
            while (source.hasRemaining()) {
                target.write(source);
            }
            target.force(true);
        } catch (IOException failure) {
            rollback(before);
            throw failure;
        }
    }

    /**
     * Заменить содержимое журнала указанными строками: обслуживание через {@code /eco compact} и выгрузка
     * снимка. Новый файл пишется рядом и подменяет старый одним переносом после сброса на диск, поэтому
     * сбой на любом шаге оставляет прежний журнал нетронутым: обрезка на месте теряла бы хвост без
     * возврата, и повтор платежа в окне идемпотентности проводился бы второй раз.
     */
    synchronized void rewrite(List<String> lines) throws IOException {
        Path temporary = path.resolveSibling(
            path.getFileName()
                .toString() + TEMPORARY_SUFFIX);
        try {
            writeAll(temporary, lines);
            close();
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            discard(temporary);
            throw failure;
        }
        untrusted = false;
    }

    private static void writeAll(Path target, List<String> lines) throws IOException {
        FileChannel fresh = FileChannel
            .open(target, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            ByteBuffer source = ByteBuffer.wrap(encoded(lines).getBytes(StandardCharsets.UTF_8));
            while (source.hasRemaining()) {
                fresh.write(source);
            }
            fresh.force(true);
        } finally {
            fresh.close();
        }
    }

    private static String encoded(List<String> lines) {
        StringBuilder tail = new StringBuilder();
        for (String line : lines) {
            tail.append(line)
                .append(LINE_SEPARATOR);
        }
        return tail.toString();
    }

    private static void discard(Path temporary) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
            // остаётся лежать рядом: следующая обрезка затрёт его сама
        }
    }

    synchronized void reset() throws IOException {
        close();
        Files.deleteIfExists(path);
        untrusted = false;
    }

    synchronized void close() throws IOException {
        if (channel != null) {
            channel.close();
            channel = null;
        }
    }

    private void rollback(long size) {
        try {
            if (channel != null && channel.isOpen()) {
                channel.truncate(size);
                channel.force(true);
            }
        } catch (IOException ignored) {
            rollbackFailed = true;
            untrusted = true;
        }
        try {
            close();
        } catch (IOException ignored) {
            rollbackFailed = true;
            untrusted = true;
        }
    }

    private FileChannel channel() throws IOException {
        if (channel != null) {
            return channel;
        }
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        channel = FileChannel
            .open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        return channel;
    }
}
