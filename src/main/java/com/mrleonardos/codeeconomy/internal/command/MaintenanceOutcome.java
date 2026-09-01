package com.mrleonardos.codeeconomy.internal.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.mrleonardos.codeeconomy.api.model.ResultCode;

public final class MaintenanceOutcome {

    private final ResultCode code;
    private final long number;
    private final List<Object> values;
    private final List<Row> rows;

    private MaintenanceOutcome(ResultCode code, long number, List<Object> values, List<Row> rows) {
        this.code = code;
        this.number = number;
        this.values = values;
        this.rows = rows;
    }

    public static MaintenanceOutcome success(long number, Object... values) {
        return new MaintenanceOutcome(null, number, copy(values), new ArrayList<>());
    }

    public static MaintenanceOutcome success(long number, List<Row> rows, Object... values) {
        return new MaintenanceOutcome(null, number, copy(values), copyRows(rows));
    }

    public static MaintenanceOutcome failure(ResultCode code) {
        Objects.requireNonNull(code, "code");
        return new MaintenanceOutcome(code, 0L, new ArrayList<>(), new ArrayList<>());
    }

    public boolean successful() {
        return code == null;
    }

    public Optional<ResultCode> code() {
        return Optional.ofNullable(code);
    }

    public long number() {
        return number;
    }

    public List<Object> values() {
        return values;
    }

    public List<Row> rows() {
        return rows;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MaintenanceOutcome)) {
            return false;
        }
        MaintenanceOutcome that = (MaintenanceOutcome) other;
        return number == that.number && code == that.code && values.equals(that.values) && rows.equals(that.rows);
    }

    @Override
    public int hashCode() {
        return ((Objects.hashCode(code) * 31 + Long.hashCode(number)) * 31 + values.hashCode()) * 31 + rows.hashCode();
    }

    @Override
    public String toString() {
        return (successful() ? "success " + number : "failure " + code) + " " + values + " " + rows;
    }

    private static List<Object> copy(Object[] source) {
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(source)));
    }

    private static List<Row> copyRows(List<Row> source) {
        for (Row row : source) {
            Objects.requireNonNull(row, "row");
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    public static final class Row {

        private final String key;
        private final List<Object> arguments;

        private Row(String key, List<Object> arguments) {
            this.key = key;
            this.arguments = arguments;
        }

        public static Row of(String key, Object... arguments) {
            Objects.requireNonNull(key, "key");
            return new Row(key, copy(arguments));
        }

        public String key() {
            return key;
        }

        public List<Object> arguments() {
            return arguments;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Row)) {
                return false;
            }
            Row that = (Row) other;
            return key.equals(that.key) && arguments.equals(that.arguments);
        }

        @Override
        public int hashCode() {
            return key.hashCode() * 31 + arguments.hashCode();
        }

        @Override
        public String toString() {
            return key + " " + arguments;
        }
    }
}
