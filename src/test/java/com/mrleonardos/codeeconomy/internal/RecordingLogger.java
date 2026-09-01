package com.mrleonardos.codeeconomy.internal;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;

/** Логгер в памяти: тесты аудита читают строки по уровням вместо ловли вывода на консоль. */
public final class RecordingLogger {

    private final List<String> infos = new ArrayList<>();
    private final List<String> warns = new ArrayList<>();
    private final List<String> debugs = new ArrayList<>();

    public Logger logger() {
        return (Logger) Proxy
            .newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Logger.class }, (proxy, method, args) -> {
                String name = method.getName();
                if (name.equals("equals") || name.equals("hashCode") || name.equals("toString")) {
                    return method.invoke(this, args);
                }
                record(name, args);
                return null;
            });
    }

    public List<String> infos() {
        return infos;
    }

    public List<String> warns() {
        return warns;
    }

    public List<String> debugs() {
        return debugs;
    }

    public boolean anyInfoContains(String part) {
        return contains(infos, part);
    }

    public boolean anyWarnContains(String part) {
        return contains(warns, part);
    }

    public boolean anyDebugContains(String part) {
        return contains(debugs, part);
    }

    private static boolean contains(List<String> lines, String part) {
        for (String line : lines) {
            if (line.contains(part)) {
                return true;
            }
        }
        return false;
    }

    private void record(String level, Object[] args) {
        String line = format(args);
        if (level.startsWith("info")) {
            infos.add(line);
        } else if (level.startsWith("warn")) {
            warns.add(line);
        } else if (level.startsWith("debug")) {
            debugs.add(line);
        }
    }

    /**
     * Склеить шаблон с параметрами. У varargs-метода прокси видит второй аргумент как массив
     * параметров, поэтому оба вида вызова: и развернутый, и свёрнутый в массив.
     */
    private static String format(Object[] args) {
        if (args == null || args.length == 0 || !(args[0] instanceof String)) {
            return args == null ? "" : String.valueOf(args[0]);
        }
        Object[] params = args.length == 2 && args[1] instanceof Object[] ? (Object[]) args[1] : tailOf(args);
        StringBuilder line = new StringBuilder((String) args[0]);
        for (Object value : params) {
            int placeholder = line.indexOf("{}");
            String text = value == null ? "null" : String.valueOf(value);
            if (placeholder < 0) {
                line.append(' ')
                    .append(text);
                continue;
            }
            line.replace(placeholder, placeholder + 2, text);
        }
        return line.toString();
    }

    private static Object[] tailOf(Object[] args) {
        Object[] tail = new Object[args.length - 1];
        System.arraycopy(args, 1, tail, 0, tail.length);
        return tail;
    }
}
