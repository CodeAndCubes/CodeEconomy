package com.mrleonardos.codeeconomy.internal.store;

import java.util.OptionalLong;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;

final class JournalCodec {

    private static final String VERSION = "v";
    private static final String SEQ = "seq";
    private static final String TS = "ts";
    private static final String TRANSACTION_ID = "transactionId";
    private static final String KIND = "kind";
    private static final String CURRENCY_ID = "currencyId";
    private static final String FROM = "from";
    private static final String FROM_AFTER = "fromAfter";
    private static final String TO = "to";
    private static final String TO_AFTER = "toAfter";
    private static final String CAUSE = "cause";
    private static final String ACTOR = "actor";
    private static final String REASON = "reason";

    private JournalCodec() {}

    static String encode(TransactionRecord record) {
        JsonObject data = new JsonObject();
        data.addProperty(VERSION, Integer.valueOf(record.version()));
        data.addProperty(SEQ, Long.valueOf(record.seq()));
        data.addProperty(TS, Long.valueOf(record.ts()));
        data.addProperty(TRANSACTION_ID, record.transactionId());
        data.addProperty(
            KIND,
            record.kind()
                .name());
        data.addProperty(CURRENCY_ID, record.currencyId());
        if (record.from()
            .isPresent()) {
            data.addProperty(
                FROM,
                record.from()
                    .get()
                    .toString());
            data.addProperty(
                FROM_AFTER,
                Long.valueOf(
                    record.fromAfter()
                        .getAsLong()));
        }
        if (record.to()
            .isPresent()) {
            data.addProperty(
                TO,
                record.to()
                    .get()
                    .toString());
            data.addProperty(
                TO_AFTER,
                Long.valueOf(
                    record.toAfter()
                        .getAsLong()));
        }
        data.addProperty(
            CAUSE,
            record.cause()
                .name());
        if (record.actor()
            .isPresent()) {
            data.addProperty(
                ACTOR,
                record.actor()
                    .get()
                    .toString());
        }
        if (record.reason()
            .isPresent()) {
            data.addProperty(
                REASON,
                record.reason()
                    .get());
        }
        return data.toString();
    }

    static TransactionRecord decode(String line) {
        JsonElement parsed;
        try {
            parsed = new JsonParser().parse(line);
        } catch (JsonParseException malformed) {
            return null;
        }
        if (parsed == null || !parsed.isJsonObject()) {
            return null;
        }
        JsonObject data = parsed.getAsJsonObject();
        String kind = text(data.get(KIND));
        String currencyId = text(data.get(CURRENCY_ID));
        String transactionId = text(data.get(TRANSACTION_ID));
        long seq = longOf(data.get(SEQ), -1L);
        long ts = longOf(data.get(TS), -1L);
        String cause = text(data.get(CAUSE));
        if (kind == null || currencyId == null || transactionId == null || cause == null || seq < 0L || ts < 0L) {
            return null;
        }
        TransactionRecord.Builder builder;
        try {
            builder = TransactionRecord.builder(TransactionRecord.Kind.valueOf(kind), currencyId, transactionId)
                .seq(seq)
                .ts(ts)
                .version(integer(data.get(VERSION), TransactionRecord.CURRENT_VERSION))
                .cause(ChangeCause.valueOf(cause));
        } catch (RuntimeException unknown) {
            return null;
        }
        UUID from = uuidOf(text(data.get(FROM)));
        UUID to = uuidOf(text(data.get(TO)));
        OptionalLong fromAfter = number(data.get(FROM_AFTER));
        OptionalLong toAfter = number(data.get(TO_AFTER));
        if ((from != null) != fromAfter.isPresent() || (to != null) != toAfter.isPresent()) {
            return null;
        }
        if (from != null) {
            builder.from(from, fromAfter.getAsLong());
        }
        if (to != null) {
            builder.to(to, toAfter.getAsLong());
        }
        UUID actor = uuidOf(text(data.get(ACTOR)));
        if (actor != null) {
            builder.actor(actor);
        }
        String reason = text(data.get(REASON));
        if (reason != null) {
            builder.reason(reason);
        }
        try {
            return builder.build();
        } catch (RuntimeException invalid) {
            return null;
        }
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

    /**
     * Число или пустой ответ. Сторона без разбираемого after-баланса делает запись негодной целиком:
     * replay ставит балансы абсолютным присваиванием, и подстановка нуля вместо потерянного значения
     * молча обнулила бы счёт вместо того, чтобы поднять карантин.
     */
    private static OptionalLong number(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(element.getAsLong());
        } catch (RuntimeException malformed) {
            return OptionalLong.empty();
        }
    }

    private static long longOf(JsonElement element, long fallback) {
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsLong();
        } catch (RuntimeException malformed) {
            return fallback;
        }
    }

    private static int integer(JsonElement element, int fallback) {
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException malformed) {
            return fallback;
        }
    }
}
