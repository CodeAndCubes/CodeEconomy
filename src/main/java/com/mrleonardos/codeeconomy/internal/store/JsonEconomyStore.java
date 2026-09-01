package com.mrleonardos.codeeconomy.internal.store;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codecore.api.config.Migration;
import com.mrleonardos.codeeconomy.api.EconomyLimits;
import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;
import com.mrleonardos.codeeconomy.api.store.ChangeBatch;
import com.mrleonardos.codeeconomy.api.store.CheckpointResult;
import com.mrleonardos.codeeconomy.api.store.EconomyStore;
import com.mrleonardos.codeeconomy.api.store.StoreMaintenance;
import com.mrleonardos.codeeconomy.api.store.StoreResult;
import com.mrleonardos.codeeconomy.api.store.StoreSnapshot;
import com.mrleonardos.codeeconomy.api.store.StoreVerification;
import com.mrleonardos.codeeconomy.internal.EconomySettings;

/**
 * Встроенное хранилище: чекпоинт {@code accounts.json} через ConfigService ядра и append-only журнал
 * {@code journal.jsonl}, который мод ведёт сам.
 *
 * <p>
 * Журнал и есть истина: {@code apply} дописывает строку с принудительным сбросом на диск и только
 * после этого счета считаются проведёнными. Отказ записи виден вызывающему как отказ, состояния в
 * памяти он не меняет, а файл возвращается к прежней длине, поэтому хвост журнала остаётся чистым.
 *
 * <p>
 * Чекпоинт всегда пишется полным набором счетов вместе с границей, которую они покрывают: граница,
 * ушедшая вперёд счетов, означала бы, что следующее восстановление пропустит записи журнала и деньги
 * исчезнут. Поэтому и {@code checkpoint}, и {@code save} принимают состояние движка целиком.
 *
 * <p>
 * {@code /eco compact} обрезает журнал, но оставляет записи свежее окна идемпотентности: хвост
 * читается с диска, а не из истории в памяти, потому что история ограничена по числу записей и
 * возрасту и на живом сервере окна не покрывает.
 */
public final class JsonEconomyStore implements EconomyStore, StoreMaintenance {

    public static final String ID = "json";
    public static final String JOURNAL_FILE = "journal.jsonl";

    private final SnapshotWriter writer;
    private final ConfigFile<JsonObject> checkpointFile;
    private final Recovery.StartBalances start;
    private final long idempotencyMillis;
    private final LongSupplier clock;
    private final Logger log;

    private JournalAppender journal;

    public JsonEconomyStore(ConfigFile<JsonObject> checkpointFile, EconomyLimits limits, Recovery.StartBalances start,
        long idempotencyMillis, LongSupplier clock, Logger log) {
        this.writer = new SnapshotWriter(checkpointFile, limits, log);
        this.checkpointFile = checkpointFile;
        this.start = start;
        this.idempotencyMillis = idempotencyMillis;
        this.clock = clock;
        this.log = log;
    }

    public static ConfigSpec<JsonObject> spec() {
        ConfigSpec.Builder<JsonObject> builder = ConfigSpec
            .of(EconomySettings.MODID, EconomySettings.ACCOUNTS_FILE, JsonObject.class)
            .scope(ConfigScope.WORLD_STATE)
            .schemaVersion(SchemaMigrations.ACCOUNTS_VERSION);
        for (Migration migration : SchemaMigrations.accountsChain()) {
            builder.migration(migration);
        }
        return builder.defaults(JsonEconomyStore::defaults)
            .build();
    }

    public static JsonObject defaults() {
        JsonObject data = new JsonObject();
        data.addProperty(SnapshotWriter.CHECKPOINT_FIELD, Long.valueOf(0L));
        data.add(SnapshotWriter.ACCOUNTS_FIELD, new JsonObject());
        return data;
    }

    public static Path journalPath(ConfigFile<JsonObject> checkpointFile) {
        Path parent = checkpointFile.path()
            .getParent();
        return parent == null ? Paths.get(JOURNAL_FILE) : parent.resolve(JOURNAL_FILE);
    }

    /** Стартовые балансы валют: на них опирается счёт, которого журнал ещё не касался. */
    public static Recovery.StartBalances starting(List<CurrencyRecord> currencies) {
        return Currencies.startBalances(currencies);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public StoreSnapshot load() {
        Path journalPath = journalPath(checkpointFile);
        SnapshotWriter.Checkpoint checkpoint = writer.read();
        Recovery.Result result = Recovery
            .recover(checkpoint.accounts(), journalPath, checkpoint.checkpointSeq(), start, log);
        if (result.corruption() != null || result.unreadable()) {
            return quarantine(result, checkpoint, journalPath);
        }
        StoreSnapshot snapshot = StoreSnapshot.of(result.accounts(), checkpoint.checkpointSeq(), result.records())
            .withFindings(result.findings());
        if (checkpoint.quarantine() != null) {
            log.error(
                "Storage still carries the quarantine mark from {}: {}. The mod stays read only until /eco unlock",
                Long.valueOf(
                    checkpoint.quarantine()
                        .at()),
                checkpoint.quarantine()
                    .reason());
            return snapshot.readOnly(
                checkpoint.quarantine()
                    .reason());
        }
        if (result.replayed() > 0L) {
            writer.markDirty();
        }
        log.info(
            "Recovery replayed {} record(s) after checkpoint {}, {} mismatch(es)",
            Long.valueOf(result.replayed()),
            Long.valueOf(checkpoint.checkpointSeq()),
            Integer.valueOf(
                result.findings()
                    .size()));
        return snapshot;
    }

    /**
     * Битый или нечитаемый журнал: то, что удалось поднять, сразу уходит в чекпоинт, журнал убирается,
     * а признак карантина ложится в состояние мира. Без признака следующий старт увидел бы здоровый
     * чекпоинт и молча продолжил работу с потерянными деньгами.
     */
    private StoreSnapshot quarantine(Recovery.Result result, SnapshotWriter.Checkpoint checkpoint, Path journalPath) {
        Path moved = Quarantine.quarantine(journalPath, log);
        closeJournal();
        if (moved != null) {
            try {
                journal().reset();
            } catch (IOException failure) {
                log.error("Failed to reset the journal after quarantine: {}", failure.toString());
            }
        } else {
            log.error(
                "Damaged journal {} stays in place because the move failed, the mod keeps it for inspection",
                journalPath);
        }
        String reason = result.unreadable() ? "journal " + journalPath.getFileName() + " cannot be read"
            : "journal is quarantined at " + (moved == null ? journalPath : moved.getFileName())
                + ", lost seq range "
                + result.corruption();
        long recovered = Math.max(checkpoint.checkpointSeq(), highestSeq(result.records()));
        Quarantine.Mark mark = new Quarantine.Mark(clock.getAsLong(), reason);
        try {
            writer.write(result.accounts(), recovered, mark);
        } catch (RuntimeException failure) {
            log.error("Failed to record the quarantine mark in {}: {}", writer.path(), failure.toString());
        }
        return StoreSnapshot.of(result.accounts(), recovered, result.records())
            .withFindings(result.findings())
            .readOnly(reason);
    }

    @Override
    public StoreResult apply(ChangeBatch batch) {
        if (batch.records()
            .isEmpty()) {
            writer.markDirty();
            return StoreResult.success();
        }
        StringBuilder encoded = new StringBuilder();
        for (TransactionRecord record : batch.records()) {
            if (encoded.length() > 0) {
                encoded.append('\n');
            }
            encoded.append(JournalCodec.encode(record));
        }
        try {
            if (!journal().trusted()) {
                return StoreResult.failure(
                    StoreResult.Failure.WRITE_FAILED,
                    "the journal tail is untrusted after a refused write, a restart rebuilds it");
            }
            journal().append(encoded.toString());
        } catch (IOException failure) {
            if (journal().rollbackFailed()) {
                log.error(
                    "Failed to roll the journal back after a refused write, the tail may hold a partial line: {}",
                    failure.toString());
            }
            return StoreResult.failure(StoreResult.Failure.WRITE_FAILED, failure.toString());
        }
        writer.markDirty();
        return StoreResult.success();
    }

    @Override
    public StoreResult save(StoreSnapshot snapshot) {
        List<String> tail = idempotencyTail();
        try {
            writer.write(snapshot.accounts(), snapshot.lastSeq(), null);
        } catch (RuntimeException failure) {
            return StoreResult.failure(StoreResult.Failure.WRITE_FAILED, failure.toString());
        }
        try {
            journal().rewrite(tail);
        } catch (IOException failure) {
            return StoreResult.failure(StoreResult.Failure.WRITE_FAILED, failure.toString());
        }
        return StoreResult.success();
    }

    @Override
    public CheckpointResult checkpoint(StoreSnapshot state) {
        if (!writer.dirty()) {
            return CheckpointResult.upToDate(writer.checkpointSeq());
        }
        try {
            writer.write(state.accounts(), state.lastSeq(), null);
        } catch (RuntimeException failure) {
            return CheckpointResult.failure(StoreResult.failure(StoreResult.Failure.WRITE_FAILED, failure.toString()));
        }
        return CheckpointResult.written(state.lastSeq());
    }

    @Override
    public StoreVerification verify(Map<UUID, AccountView> accounts, long upToSeq) {
        SnapshotWriter.Checkpoint checkpoint = writer.read();
        return Recovery.verifyFromCheckpoint(
            checkpoint.accounts(),
            checkpoint.checkpointSeq(),
            journalPath(checkpointFile),
            accounts,
            upToSeq,
            start);
    }

    @Override
    public StoreResult liftReadOnly() {
        try {
            writer.clearQuarantine();
        } catch (RuntimeException failure) {
            return StoreResult.failure(StoreResult.Failure.WRITE_FAILED, failure.toString());
        }
        return StoreResult.success();
    }

    /**
     * Хвост журнала, который переживает обрезку: строки свежее окна идемпотентности, прочитанные с
     * диска. Из кольцевой истории его строить нельзя: она ограничена по числу записей, и на оживлённом
     * сервере из неё вытесняются операции, чьё окно ещё не вышло.
     */
    private List<String> idempotencyTail() {
        if (idempotencyMillis <= 0L) {
            return Collections.emptyList();
        }
        List<String> lines = Recovery.lines(journalPath(checkpointFile));
        if (lines == null) {
            log.warn("Journal cannot be read while compacting, the idempotency tail is dropped");
            return Collections.emptyList();
        }
        long cutoff = clock.getAsLong() - idempotencyMillis;
        List<String> tail = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            TransactionRecord record = JournalCodec.decode(trimmed);
            if (record != null && record.ts() >= cutoff) {
                tail.add(trimmed);
            }
        }
        return tail;
    }

    public Path journalPath() {
        return journal().path();
    }

    @Override
    public void close() {
        try {
            journal().close();
        } catch (IOException failure) {
            log.warn("Failed to close the journal on shutdown: {}", failure.toString());
        }
    }

    private void closeJournal() {
        try {
            journal().close();
        } catch (IOException failure) {
            log.warn("Failed to close the journal before the move: {}", failure.toString());
        }
    }

    /**
     * Журнал открывается по первому обращению: путь берётся у чекпоинта, а тот известен только после
     * загрузки мира.
     */
    private synchronized JournalAppender journal() {
        if (journal == null) {
            journal = new JournalAppender(journalPath(checkpointFile));
        }
        return journal;
    }

    private static long highestSeq(List<TransactionRecord> records) {
        long highest = 0L;
        for (TransactionRecord record : records) {
            highest = Math.max(highest, record.seq());
        }
        return highest;
    }
}
