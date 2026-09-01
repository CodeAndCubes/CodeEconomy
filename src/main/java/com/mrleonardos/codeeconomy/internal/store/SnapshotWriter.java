package com.mrleonardos.codeeconomy.internal.store;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codeeconomy.api.EconomyLimits;
import com.mrleonardos.codeeconomy.api.model.AccountView;

/**
 * Чекпоинт счетов: чтение файла, накопление изменений и запись через ConfigService ядра.
 *
 * <p>
 * Файл остаётся согласован сам с собой: счета и граница чекпоинта живут в одном json и пишутся одной
 * атомарной подменой, поэтому после обрыва процесса восстановление переигрывает только строки журнала
 * с {@code seq} больше границы.
 *
 * <p>
 * Накопленные счета держатся в собственном поле, а не в живом объекте конфига: перечитывание настроек
 * ядром накопленное не теряет. Чтение, накопление и запись идут под одним локом, поэтому сверка
 * {@code verify} из фонового потока видит файл либо до записи, либо после, но не посередине.
 */
public final class SnapshotWriter {

    public static final String ACCOUNTS_FIELD = "accounts";
    public static final String CHECKPOINT_FIELD = "checkpointSeq";

    public static final String UUID_FIELD = "uuid";
    public static final String NAME_FIELD = "name";
    public static final String BALANCES_FIELD = "balances";
    public static final String FROZEN_FIELD = "frozen";
    public static final String CREATED_AT_FIELD = "createdAt";

    private final ConfigFile<JsonObject> file;
    private final EconomyLimits limits;
    private final Logger log;
    private final Object lock = new Object();

    private final Map<UUID, AccountView> staged = new LinkedHashMap<>();
    private long stagedSeq;
    private boolean dirty;

    public SnapshotWriter(ConfigFile<JsonObject> file, EconomyLimits limits, Logger log) {
        this.file = file;
        this.limits = limits;
        this.log = log;
    }

    public Checkpoint read() {
        synchronized (lock) {
            JsonObject data = file.get();
            long checkpointSeq = longOf(data.get(CHECKPOINT_FIELD));
            Map<UUID, AccountView> accounts = new LinkedHashMap<>();
            JsonElement stored = data.get(ACCOUNTS_FIELD);
            if (stored != null && stored.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : stored.getAsJsonObject()
                    .entrySet()) {
                    if (accounts.size() >= limits.accounts()) {
                        warn(
                            "Checkpoint holds more than {} accounts, the rest are skipped",
                            Integer.valueOf(limits.accounts()));
                        break;
                    }
                    if (!entry.getValue()
                        .isJsonObject()) {
                        warn("Account {} is not an object and was skipped", entry.getKey());
                        continue;
                    }
                    AccountView account = readAccount(
                        entry.getKey(),
                        entry.getValue()
                            .getAsJsonObject());
                    if (account == null) {
                        warn("Account {} is unusable and was skipped", entry.getKey());
                        continue;
                    }
                    accounts.put(account.uuid(), account);
                }
            }
            return new Checkpoint(accounts, checkpointSeq);
        }
    }

    /** Внести счета в накопитель и подвинуть границу чекпоинта. До записи на диск дело не доходит. */
    public void stage(List<AccountView> upserts, long seq) {
        synchronized (lock) {
            for (AccountView account : upserts) {
                staged.put(account.uuid(), account);
            }
            if (seq > stagedSeq) {
                stagedSeq = seq;
            }
            dirty = true;
        }
    }

    /** Записать файл, если есть что записывать. */
    public boolean saveIfDirty() {
        synchronized (lock) {
            if (!dirty) {
                return false;
            }
            long seq = Math.max(
                longOf(
                    file.get()
                        .get(CHECKPOINT_FIELD)),
                stagedSeq);
            write(new ArrayList<>(staged.values()), seq);
            staged.clear();
            stagedSeq = 0L;
            dirty = false;
            return true;
        }
    }

    /** Записать файл с указанной границей чекпоинта, вместе с накопленными счетами. */
    public void save(long checkpointSeq) {
        synchronized (lock) {
            List<AccountView> accounts = new ArrayList<>(staged.values());
            write(accounts, checkpointSeq);
            staged.clear();
            stagedSeq = 0L;
            dirty = false;
        }
    }

    public Path path() {
        return file.path();
    }

    private void write(List<AccountView> upserts, long checkpointSeq) {
        JsonObject data = file.get();
        JsonObject stored = accountsOf(data);
        for (AccountView account : upserts) {
            stored.add(
                account.uuid()
                    .toString(),
                encode(account));
        }
        data.add(ACCOUNTS_FIELD, stored);
        data.addProperty(CHECKPOINT_FIELD, Long.valueOf(checkpointSeq));
        file.save();
    }

    private static JsonObject accountsOf(JsonObject data) {
        JsonElement stored = data.get(ACCOUNTS_FIELD);
        if (stored != null && stored.isJsonObject()) {
            return stored.getAsJsonObject();
        }
        return new JsonObject();
    }

    private AccountView readAccount(String key, JsonObject data) {
        UUID uuid = uuidOf(key);
        if (uuid == null) {
            uuid = uuidOf(text(data.get(UUID_FIELD)));
        }
        if (uuid == null) {
            return null;
        }
        Map<String, Long> balances = new LinkedHashMap<>();
        JsonElement stored = data.get(BALANCES_FIELD);
        if (stored != null && stored.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : stored.getAsJsonObject()
                .entrySet()) {
                JsonElement amount = entry.getValue();
                if (amount != null && amount.isJsonPrimitive()) {
                    balances.put(entry.getKey(), Long.valueOf(amount.getAsLong()));
                }
            }
        }
        long createdAt = longOf(data.get(CREATED_AT_FIELD));
        boolean frozen = booleanOf(data.get(FROZEN_FIELD));
        return AccountView.of(uuid, text(data.get(NAME_FIELD)), balances, frozen, createdAt);
    }

    private static JsonObject encode(AccountView account) {
        JsonObject data = new JsonObject();
        data.addProperty(
            UUID_FIELD,
            account.uuid()
                .toString());
        if (account.name()
            .isPresent()) {
            data.addProperty(
                NAME_FIELD,
                account.name()
                    .get());
        }
        JsonObject balances = new JsonObject();
        for (Map.Entry<String, Long> balance : account.balances()
            .entrySet()) {
            balances.addProperty(
                balance.getKey(),
                Long.valueOf(
                    balance.getValue()
                        .longValue()));
        }
        data.add(BALANCES_FIELD, balances);
        data.addProperty(FROZEN_FIELD, Boolean.valueOf(account.frozen()));
        data.addProperty(CREATED_AT_FIELD, Long.valueOf(account.createdAt()));
        return data;
    }

    private static UUID uuidOf(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static String text(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }

    private static long longOf(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return 0L;
        }
        try {
            return element.getAsLong();
        } catch (RuntimeException malformed) {
            return 0L;
        }
    }

    private static boolean booleanOf(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return false;
        }
        try {
            return element.getAsBoolean();
        } catch (RuntimeException malformed) {
            return false;
        }
    }

    private void warn(String message, Object... arguments) {
        if (log != null) {
            log.warn(message, arguments);
        }
    }

    /** Содержимое чекпоинта: счета и наибольший {@code seq}, который в них попал. */
    public static final class Checkpoint {

        private final Map<UUID, AccountView> accounts;
        private final long checkpointSeq;

        Checkpoint(Map<UUID, AccountView> accounts, long checkpointSeq) {
            this.accounts = Collections.unmodifiableMap(new LinkedHashMap<>(accounts));
            this.checkpointSeq = checkpointSeq;
        }

        public Map<UUID, AccountView> accounts() {
            return accounts;
        }

        public long checkpointSeq() {
            return checkpointSeq;
        }
    }
}
