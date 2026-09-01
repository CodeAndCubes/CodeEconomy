package com.mrleonardos.codeeconomy.internal.store;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

final class JournalAppender {

    private static final String LINE_SEPARATOR = "\n";

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

    /** Заменить содержимое журнала указанными строками: обслуживание через {@code /eco compact}. */
    synchronized void rewrite(List<String> lines) throws IOException {
        FileChannel target = channel();
        target.truncate(0L);
        StringBuilder tail = new StringBuilder();
        for (String line : lines) {
            tail.append(line)
                .append(LINE_SEPARATOR);
        }
        ByteBuffer source = ByteBuffer.wrap(
            tail.toString()
                .getBytes(StandardCharsets.UTF_8));
        while (source.hasRemaining()) {
            target.write(source);
        }
        target.force(true);
        untrusted = false;
    }

    synchronized void truncate() throws IOException {
        if (channel == null && !Files.exists(path)) {
            return;
        }
        rewrite(java.util.Collections.<String>emptyList());
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
