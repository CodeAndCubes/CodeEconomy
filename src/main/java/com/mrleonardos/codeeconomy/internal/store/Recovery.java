package com.mrleonardos.codeeconomy.internal.store;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;
import com.mrleonardos.codeeconomy.api.store.StoreVerification;

/**
 * Переигрывание журнала: восстановление состояния при старте и сверка after-балансов.
 *
 * <p>
 * Истина это журнал, поэтому каждая строка несёт итоговые балансы сторон. Переигрывание пересчитывает
 * балансы и сверяет их с записанными, любое расхождение попадает в отчёт. Оборванный хвост append-only
 * файла это штатная потеря последней записи, битая строка в середине означает потерянные деньги и
 * уводит журнал в карантин.
 */
public final class Recovery {

    private static final char LINE_SEPARATOR = '\n';

    private Recovery() {}

    /** Стартовые балансы по валютам: на них опирается счёт, которого журнал ещё не касался. */
    public interface StartBalances {

        long starting(String currencyId);
    }

    /**
     * Восстановить состояние: чекпоинт плюс строки журнала с {@code seq} больше границы чекпоинта.
     * Строки с меньшим номером уже в чекпоинте, в списке они остаются для истории, но балансы не
     * двигают. Нечитаемый файл не считается пустым: восстановление отвечает {@code unreadable}.
     */
    public static Result recover(Map<UUID, AccountView> checkpoint, Path journal, long checkpointSeq,
        StartBalances start, Logger log) {
        List<String> lines = lines(journal);
        Replay replay = new Replay(checkpoint, start);
        if (lines == null) {
            if (log != null) {
                log.error("Journal {} exists but cannot be read", journal);
            }
            replay.unreadable = true;
            return replay.result();
        }
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index)
                .trim();
            if (line.isEmpty()) {
                continue;
            }
            TransactionRecord record = JournalCodec.decode(line);
            if (record == null) {
                if (index == lastNonBlank(lines)) {
                    if (log != null) {
                        log.warn(
                            "Journal {} ends with an incomplete line {}, it is dropped",
                            journal.getFileName(),
                            Integer.valueOf(index + 1));
                    }
                    replay.tailTruncated = true;
                    break;
                }
                long[] range = lostRange(lines, index);
                if (log != null) {
                    log.warn(
                        "Journal {} is damaged at line {}, records from seq {} to seq {} are lost",
                        journal.getFileName(),
                        Integer.valueOf(index + 1),
                        Long.valueOf(range[0]),
                        Long.valueOf(range[1]));
                }
                replay.corruption = new Corruption(range[0], range[1]);
                break;
            }
            if (record.seq() > checkpointSeq) {
                replay.step(record);
            } else {
                replay.records.add(record);
            }
        }
        if (log != null && !replay.findings.isEmpty()) {
            log.warn(
                "{} mismatch(es) between journal and balances, first: {}",
                Integer.valueOf(replay.findings.size()),
                replay.findings.get(0));
        }
        return replay.result();
    }

    /**
     * Сверка по кольцевой истории в памяти. Расхождение внутри записи попадает в отчёт всегда, итог
     * журнала сравнивается с балансами счетов только когда история полная: переигранное не знает про
     * записи, вытесненные по числу или возрасту, и молчит об этом.
     */
    public static List<String> verify(Map<UUID, AccountView> current, List<TransactionRecord> newestFirst,
        StartBalances start, boolean completeHistory) {
        List<TransactionRecord> oldestFirst = new ArrayList<>(newestFirst);
        Collections.reverse(oldestFirst);
        Replay replay = new Replay(new LinkedHashMap<UUID, AccountView>(), start);
        for (TransactionRecord record : oldestFirst) {
            replay.step(record);
        }
        List<String> findings = new ArrayList<>(replay.findings);
        if (completeHistory) {
            compare(replay, current, start, findings);
        }
        return findings;
    }

    /**
     * Сверка по файлу журнала от чекпоинта: строки с {@code seq} больше границы ложатся на счета
     * чекпоинта, итог сравнивается с переданными счетами. Работает и после того, как кольцевая история
     * вытеснила записи, потому что читает носитель, а не память.
     *
     * <p>
     * Верхняя граница {@code upToSeq} снимается вместе со счетами. Без неё сверка на живом сервере
     * ловила бы каждый {@code /pay}, прошедший пока она читала файл, и отчёт называл бы расхождением
     * запись, которой в снимке счетов просто ещё нет.
     */
    public static StoreVerification verifyFromCheckpoint(Map<UUID, AccountView> checkpoint, long checkpointSeq,
        Path journal, Map<UUID, AccountView> current, long upToSeq, StartBalances start) {
        List<String> lines = lines(journal);
        if (lines == null) {
            return StoreVerification.voided("journal " + journal.getFileName() + " cannot be read, the check is void");
        }
        Replay replay = new Replay(checkpoint, start);
        long settled = 0L;
        long ahead = 0L;
        List<Integer> damaged = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String trimmed = lines.get(index)
                .trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            TransactionRecord record = JournalCodec.decode(trimmed);
            if (record == null) {
                damaged.add(Integer.valueOf(index + 1));
                continue;
            }
            if (record.seq() <= checkpointSeq) {
                settled++;
                continue;
            }
            if (record.seq() > upToSeq) {
                ahead++;
                continue;
            }
            replay.step(record);
        }
        List<String> findings = new ArrayList<>(replay.findings);
        if (!damaged.isEmpty()) {
            findings.add(damaged.size() + " unreadable line(s) in the journal: " + damaged);
        }
        compare(replay, current, start, findings);
        return StoreVerification.of(findings, settled, ahead, damaged.size());
    }

    private static void compare(Replay replay, Map<UUID, AccountView> current, StartBalances start,
        List<String> findings) {
        Set<UUID> players = new LinkedHashSet<>(replay.marks.keySet());
        for (UUID player : replay.accounts.keySet()) {
            if (current.containsKey(player)) {
                players.add(player);
            }
        }
        for (UUID player : players) {
            AccountView replayed = replay.accounts.get(player);
            AccountView actual = current.get(player);
            Mark mark = replay.marks.get(player);
            Set<String> currencies = new LinkedHashSet<>();
            if (replayed != null) {
                currencies.addAll(
                    replayed.balances()
                        .keySet());
            }
            if (mark != null) {
                currencies.addAll(mark.currencies);
            }
            for (String currencyId : currencies) {
                long journalHolds = balance(replayed, currencyId, start);
                long accountHolds = balance(actual, currencyId, start);
                if (journalHolds != accountHolds) {
                    findings.add(
                        "seq " + (mark == null ? 0L : mark.lastSeq)
                            + ": journal holds "
                            + journalHolds
                            + " for "
                            + currencyId
                            + ", account holds "
                            + accountHolds);
                }
            }
        }
    }

    private static long[] lostRange(List<String> lines, int from) {
        long lowest = -1L;
        long highest = -1L;
        for (int index = from + 1; index < lines.size(); index++) {
            TransactionRecord record = JournalCodec.decode(
                lines.get(index)
                    .trim());
            if (record == null) {
                continue;
            }
            lowest = lowest < 0L ? record.seq() : Math.min(lowest, record.seq());
            highest = Math.max(highest, record.seq());
        }
        return new long[] { Math.max(lowest, 0L), Math.max(highest, 0L) };
    }

    private static int lastNonBlank(List<String> lines) {
        for (int index = lines.size() - 1; index >= 0; index--) {
            if (!lines.get(index)
                .trim()
                .isEmpty()) {
                return index;
            }
        }
        return -1;
    }

    /** Строки журнала, пустой список для отсутствующего файла, {@code null} для нечитаемого. */
    static List<String> lines(Path journal) {
        if (!Files.exists(journal)) {
            return Collections.emptyList();
        }
        if (!Files.isRegularFile(journal)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(journal, StandardCharsets.UTF_8)) {
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            int symbol = reader.read();
            while (symbol >= 0) {
                if (symbol == LINE_SEPARATOR) {
                    lines.add(line.toString());
                    line.setLength(0);
                } else {
                    line.append((char) symbol);
                }
                symbol = reader.read();
            }
            if (line.length() > 0) {
                lines.add(line.toString());
            }
            return lines;
        } catch (IOException failure) {
            return null;
        }
    }

    private static long balance(AccountView account, String currencyId, StartBalances start) {
        if (account == null) {
            return start.starting(currencyId);
        }
        Long stored = account.balances()
            .get(currencyId);
        return stored == null ? start.starting(currencyId) : stored.longValue();
    }

    private static final class Replay {

        private final Map<UUID, AccountView> accounts;
        private final Map<UUID, Mark> marks = new LinkedHashMap<>();
        private final StartBalances start;
        private final List<TransactionRecord> records = new ArrayList<>();
        private final List<String> findings = new ArrayList<>();
        private long applied;
        private boolean tailTruncated;
        private boolean unreadable;
        private Corruption corruption;

        Replay(Map<UUID, AccountView> accounts, StartBalances start) {
            this.accounts = new LinkedHashMap<>(accounts);
            this.start = start;
        }

        void step(TransactionRecord record) {
            String currencyId = record.currencyId();
            if (record.from()
                .isPresent()) {
                UUID player = record.from()
                    .get();
                long before = balance(accounts.get(player), currencyId, start);
                long after = record.fromAfter()
                    .getAsLong();
                if (outgoingCannotGrow(record.kind()) && after > before) {
                    finding(record.seq(), "fromAfter is " + after + " but replay reached " + before);
                }
                apply(player, currencyId, after, record.ts(), record.seq());
            }
            if (record.to()
                .isPresent()) {
                UUID player = record.to()
                    .get();
                long before = balance(accounts.get(player), currencyId, start);
                long after = record.toAfter()
                    .getAsLong();
                if (incomingCannotShrink(record.kind()) && after < before) {
                    finding(record.seq(), "toAfter is " + after + " but replay reached " + before);
                }
                apply(player, currencyId, after, record.ts(), record.seq());
            }
            records.add(record);
            applied++;
        }

        private static boolean outgoingCannotGrow(TransactionRecord.Kind kind) {
            return kind == TransactionRecord.Kind.TRANSFER || kind == TransactionRecord.Kind.WITHDRAW;
        }

        private static boolean incomingCannotShrink(TransactionRecord.Kind kind) {
            return kind == TransactionRecord.Kind.TRANSFER || kind == TransactionRecord.Kind.DEPOSIT;
        }

        private void apply(UUID player, String currencyId, long value, long ts, long seq) {
            AccountView existing = accounts.get(player);
            Map<String, Long> balances = new LinkedHashMap<>();
            if (existing != null) {
                balances.putAll(existing.balances());
            }
            balances.put(currencyId, Long.valueOf(value));
            accounts.put(
                player,
                AccountView.of(
                    player,
                    existing == null ? null
                        : existing.name()
                            .orElse(null),
                    balances,
                    existing != null && existing.frozen(),
                    existing == null ? ts : existing.createdAt()));
            Mark mark = marks.get(player);
            if (mark == null) {
                marks.put(player, new Mark(seq, currencyId));
                return;
            }
            mark.currencies.add(currencyId);
            mark.lastSeq = Math.max(mark.lastSeq, seq);
        }

        private void finding(long seq, String text) {
            findings.add("seq " + seq + ": " + text);
        }

        Result result() {
            return new Result(accounts, records, findings, applied, tailTruncated, unreadable, corruption);
        }
    }

    private static final class Mark {

        private final Set<String> currencies = new LinkedHashSet<>();
        private long lastSeq;

        Mark(long lastSeq, String currencyId) {
            this.lastSeq = lastSeq;
            this.currencies.add(currencyId);
        }
    }

    /** Потерянный диапазон {@code seq} журнала, ушедшего в карантин. */
    public static final class Corruption {

        private final long fromSeq;
        private final long toSeq;

        Corruption(long fromSeq, long toSeq) {
            this.fromSeq = fromSeq;
            this.toSeq = toSeq;
        }

        public long fromSeq() {
            return fromSeq;
        }

        public long toSeq() {
            return toSeq;
        }

        @Override
        public String toString() {
            return fromSeq + ".." + toSeq;
        }
    }

    /** Итог восстановления. */
    public static final class Result {

        private final Map<UUID, AccountView> accounts;
        private final List<TransactionRecord> records;
        private final List<String> findings;
        private final long replayed;
        private final boolean tailTruncated;
        private final boolean unreadable;
        private final Corruption corruption;

        Result(Map<UUID, AccountView> accounts, List<TransactionRecord> records, List<String> findings, long replayed,
            boolean tailTruncated, boolean unreadable, Corruption corruption) {
            this.accounts = Collections.unmodifiableMap(new LinkedHashMap<>(accounts));
            this.records = Collections.unmodifiableList(new ArrayList<>(records));
            this.findings = Collections.unmodifiableList(new ArrayList<>(findings));
            this.replayed = replayed;
            this.tailTruncated = tailTruncated;
            this.unreadable = unreadable;
            this.corruption = corruption;
        }

        public Map<UUID, AccountView> accounts() {
            return accounts;
        }

        public List<TransactionRecord> records() {
            return records;
        }

        public List<String> findings() {
            return findings;
        }

        public long replayed() {
            return replayed;
        }

        public boolean tailTruncated() {
            return tailTruncated;
        }

        /** Правда ли журнал есть, но не читается: это та же потеря записей, что и битая середина. */
        public boolean unreadable() {
            return unreadable;
        }

        public Corruption corruption() {
            return corruption;
        }
    }
}
