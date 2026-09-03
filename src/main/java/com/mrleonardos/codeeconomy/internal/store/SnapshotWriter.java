package com.mrleonardos.codeeconomy.internal.store;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codeeconomy.api.EconomyLimits;
import com.mrleonardos.codeeconomy.api.model.AccountView;

/**
 * Чекпоинт счетов: чтение файла и запись через ConfigService ядра.
 *
 * <p>
 * Записывается всегда полный набор счетов вместе с границей, которую он покрывает, одной атомарной
 * подменой. Разницы с прежним содержимым файл не знает: граница и счета обязаны согласоваться, иначе
 * следующее восстановление пропустит записи журнала, чьи балансы в файл не попали, и деньги исчезнут
 * молча. Полный набор счетов держит движок, писателю его передают на каждую запись.
 *
 * <p>
 * Писатель помнит одно: копились ли счета с прошлой записи. Чтение и запись идут под одним локом,
 * поэтому сверка {@code verify} из фонового потока видит файл либо до записи, либо после, но не
 * посередине.
 */
public final class SnapshotWriter {

    private static final String ACCOUNTS_FIELD = "accounts";
    private static final String CHECKPOINT_FIELD = "checkpointSeq";
    private static final String QUARANTINE_FIELD = "quarantine";

    private static final String UUID_FIELD = "uuid";
    private static final String NAME_FIELD = "name";
    private static final String BALANCES_FIELD = "balances";
    private static final String FROZEN_FIELD = "frozen";
    private static final String CREATED_AT_FIELD = "createdAt";

    private static final String QUARANTINE_AT_FIELD = "at";
    private static final String QUARANTINE_REASON_FIELD = "reason";

    private final ConfigFile<JsonObject> file;
    private final EconomyLimits limits;
    private final Logger log;
    private final Object lock = new Object();

    private boolean dirty;

    public SnapshotWriter(ConfigFile<JsonObject> file, EconomyLimits limits, Logger log) {
        this.file = file;
        this.limits = limits;
        this.log = log;
    }

    /**
     * Пустой чекпоинт первого запуска: счетов нет, граница на нуле.
     *
     * <p>
     * Собирается здесь, а не у хранилища: имена ключей json знает только тот, кто их читает и пишет.
     * Наружу они не выходят ещё и потому, что вырезанная константа со значением вроде {@code uuid}
     * считалась бы утёкшей в клиентский jar, где такая же строка стоит в {@code api}.
     */
    public static JsonObject empty() {
        JsonObject data = new JsonObject();
        data.addProperty(CHECKPOINT_FIELD, Long.valueOf(0L));
        data.add(ACCOUNTS_FIELD, new JsonObject());
        return data;
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
            return new Checkpoint(accounts, checkpointSeq, readMark(data.get(QUARANTINE_FIELD)));
        }
    }

    /** Граница чекпоинта из файла без разбора счетов: для ответа, когда писать нечего. */
    public long checkpointSeq() {
        synchronized (lock) {
            return longOf(
                file.get()
                    .get(CHECKPOINT_FIELD));
        }
    }

    /** Отметить, что счета изменились: ближайшая запись перенесёт их в файл. */
    public void markDirty() {
        synchronized (lock) {
            dirty = true;
        }
    }

    /** Правда ли с прошлой записи счета менялись. */
    public boolean dirty() {
        synchronized (lock) {
            return dirty;
        }
    }

    /**
     * Записать счета целиком с границей, которую они покрывают.
     *
     * @param accounts      полный набор счетов, а не разница с прежним содержимым файла
     * @param checkpointSeq наибольший {@code seq}, учтённый в этих счетах
     * @param mark          признак карантина или null, когда носитель здоров
     */
    public void write(Map<UUID, AccountView> accounts, long checkpointSeq, Quarantine.Mark mark) {
        synchronized (lock) {
            JsonObject data = file.get();
            JsonObject stored = new JsonObject();
            for (AccountView account : accounts.values()) {
                stored.add(
                    account.uuid()
                        .toString(),
                    encode(account));
            }
            data.add(ACCOUNTS_FIELD, stored);
            data.addProperty(CHECKPOINT_FIELD, Long.valueOf(checkpointSeq));
            if (mark == null) {
                data.remove(QUARANTINE_FIELD);
            } else {
                data.add(QUARANTINE_FIELD, encode(mark));
            }
            file.save();
            dirty = false;
        }
    }

    /** Снять признак карантина, счета и границу оставить как есть. */
    public void clearQuarantine() {
        synchronized (lock) {
            JsonObject data = file.get();
            if (data.get(QUARANTINE_FIELD) == null) {
                return;
            }
            data.remove(QUARANTINE_FIELD);
            file.save();
        }
    }

    public Path path() {
        return file.path();
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

    private static Quarantine.Mark readMark(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject data = element.getAsJsonObject();
        String reason = text(data.get(QUARANTINE_REASON_FIELD));
        return new Quarantine.Mark(
            longOf(data.get(QUARANTINE_AT_FIELD)),
            reason == null ? "the storage was quarantined by an earlier start" : reason);
    }

    private static JsonObject encode(Quarantine.Mark mark) {
        JsonObject data = new JsonObject();
        data.addProperty(QUARANTINE_AT_FIELD, Long.valueOf(mark.at()));
        data.addProperty(QUARANTINE_REASON_FIELD, mark.reason());
        return data;
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

    /** Содержимое чекпоинта: счета, граница, которую они покрывают, и признак карантина. */
    public static final class Checkpoint {

        private final Map<UUID, AccountView> accounts;
        private final long checkpointSeq;
        private final Quarantine.Mark quarantine;

        Checkpoint(Map<UUID, AccountView> accounts, long checkpointSeq, Quarantine.Mark quarantine) {
            this.accounts = Collections.unmodifiableMap(new LinkedHashMap<>(accounts));
            this.checkpointSeq = checkpointSeq;
            this.quarantine = quarantine;
        }

        public Map<UUID, AccountView> accounts() {
            return accounts;
        }

        public long checkpointSeq() {
            return checkpointSeq;
        }

        /** Признак карантина, оставленный прошлым стартом, или null. */
        public Quarantine.Mark quarantine() {
            return quarantine;
        }
    }
}
