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
import com.mrleonardos.codeeconomy.api.store.EconomyStore;
import com.mrleonardos.codeeconomy.api.store.StoreResult;
import com.mrleonardos.codeeconomy.api.store.StoreSnapshot;
import com.mrleonardos.codeeconomy.internal.EconomySettings;

/**
 * Встроенное хранилище: чекпоинт {@code accounts.json} через ConfigService ядра и append-only журнал
 * {@code journal.jsonl}, который мод ведёт сам.
 *
 * <p>
 * Журнал и есть истина: {@code apply} дописывает строку с принудительным сбросом на диск и только
 * после этого вносит счета в чекпоинт. Отказ записи виден вызывающему как отказ, состояния в памяти он
 * не меняет, а файл возвращается к прежней длине, поэтому хвост журнала остаётся чистым.
 *
 * <p>
 * {@code /eco compact} обрезает журнал, но оставляет записи свежее окна идемпотентности: повтор
 * {@code transactionId} ловится и после обрезки, пока окно не вышло.
 */
public final class JsonEconomyStore implements EconomyStore {

    public static final String ID = "json";
    public static final String JOURNAL_FILE = "journal.jsonl";

    private final SnapshotWriter writer;
    private final ConfigFile<JsonObject> checkpointFile;
    private final Recovery.StartBalances start;
    private final boolean readOnlyOnCorrupt;
    private final long idempotencyMillis;
    private final LongSupplier clock;
    private final Logger log;

    private volatile List<String> findings = Collections.emptyList();
    private JournalAppender journal;

    public JsonEconomyStore(ConfigFile<JsonObject> checkpointFile, EconomyLimits limits, Recovery.StartBalances start,
        boolean readOnlyOnCorrupt, long idempotencyMillis, LongSupplier clock, Logger log) {
        this.writer = new SnapshotWriter(checkpointFile, limits, log);
        this.checkpointFile = checkpointFile;
        this.start = start;
        this.readOnlyOnCorrupt = readOnlyOnCorrupt;
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
        final Map<String, Long> starting = Currencies.startingBalances(currencies);
        return new Recovery.StartBalances() {

            @Override
            public long starting(String currencyId) {
                Long value = starting.get(currencyId);
                return value == null ? 0L : value.longValue();
            }
        };
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
        findings = result.findings();
        if (result.corruption() != null || result.unreadable()) {
            Path quarantined = Quarantine.quarantine(journalPath, log);
            closeJournal();
            if (quarantined != null) {
                try {
                    journal().reset();
                } catch (IOException failure) {
                    if (log != null) {
                        log.error("Failed to reset the journal after quarantine: {}", failure.toString());
                    }
                }
            } else if (log != null) {
                log.error(
                    "Damaged journal {} stays in place because the move failed, the mod keeps it for inspection",
                    journalPath);
            }
            String reason = result.unreadable() ? "journal " + journalPath.getFileName() + " cannot be read"
                : "journal is quarantined at " + (quarantined == null ? journalPath : quarantined)
                    + ", lost seq range "
                    + result.corruption();
            return readOnly(result, checkpoint, reason);
        }
        if (log != null) {
            log.info(
                "Recovery replayed {} record(s) after checkpoint {}, {} mismatch(es)",
                Long.valueOf(result.replayed()),
                Long.valueOf(checkpoint.checkpointSeq()),
                Integer.valueOf(
                    result.findings()
                        .size()));
        }
        return StoreSnapshot.of(result.accounts(), checkpoint.checkpointSeq(), result.records());
    }

    private StoreSnapshot readOnly(Recovery.Result result, SnapshotWriter.Checkpoint checkpoint, String reason) {
        StoreSnapshot snapshot = StoreSnapshot.of(result.accounts(), checkpoint.checkpointSeq(), result.records());
        if (!readOnlyOnCorrupt) {
            return snapshot;
        }
        return StoreSnapshot.readOnly(snapshot, reason);
    }

    private void closeJournal() {
        try {
            journal().close();
        } catch (IOException failure) {
            if (log != null) {
                log.warn("Failed to close the journal before the move: {}", failure.toString());
            }
        }
    }

    @Override
    public StoreResult apply(ChangeBatch batch) {
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
            if (journal().rollbackFailed() && log != null) {
                log.error(
                    "Failed to roll the journal back after a refused write, the tail may hold a partial line: {}",
                    failure.toString());
            }
            return StoreResult.failure(StoreResult.Failure.WRITE_FAILED, failure.toString());
        }
        writer.stage(batch.upserts(), lastSeq(batch.records()));
        return StoreResult.success();
    }

    @Override
    public StoreResult save(StoreSnapshot snapshot) {
        try {
            writer.save(snapshot.lastSeq());
            journal().rewrite(idempotencyTail(snapshot));
        } catch (IOException failure) {
            return StoreResult.failure(StoreResult.Failure.WRITE_FAILED, failure.toString());
        }
        return StoreResult.success();
    }

    /**
     * Хвост журнала, который переживает обрезку: записи свежее окна идемпотентности. Повтор
     * {@code transactionId} ловится и после {@code compact}, пока окно не вышло.
     */
    private List<String> idempotencyTail(StoreSnapshot snapshot) {
        if (idempotencyMillis <= 0L) {
            return Collections.emptyList();
        }
        long cutoff = clock.getAsLong() - idempotencyMillis;
        List<String> tail = new ArrayList<>();
        for (TransactionRecord record : snapshot.transactions()) {
            if (record.ts() >= cutoff) {
                tail.add(JournalCodec.encode(record));
            }
        }
        return tail;
    }

    /** Записать чекпоинт, если после последней записи накопились счета. Журнал не трогает. */
    public boolean flushCheckpoint() {
        return writer.saveIfDirty();
    }

    /** Расхождения, найденные при последней загрузке журнала. */
    public List<String> lastFindings() {
        return findings;
    }

    /**
     * Сверка текущих счетов с журналом: строки свежее границы чекпоинта ложатся на чекпоинт из файла,
     * итог сравнивается с тем, что движок держит в памяти. Безопасно из фонового потока.
     */
    public Recovery.Verification verify(Map<UUID, AccountView> current) {
        SnapshotWriter.Checkpoint checkpoint = writer.read();
        return Recovery.verifyFromCheckpoint(
            checkpoint.accounts(),
            checkpoint.checkpointSeq(),
            journalPath(checkpointFile),
            current,
            start);
    }

    public Path journalPath() {
        return journal().path();
    }

    public void close() throws IOException {
        journal().close();
    }

    /** Закрыть журнал при остановке сервера, отказ закрытия не поднимается наверх. */
    public void closeQuietly() {
        try {
            journal().close();
        } catch (IOException failure) {
            if (log != null) {
                log.warn("Failed to close the journal on shutdown: {}", failure.toString());
            }
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

    private static long lastSeq(List<TransactionRecord> records) {
        long highest = 0L;
        for (TransactionRecord record : records) {
            highest = Math.max(highest, record.seq());
        }
        return highest;
    }
}
