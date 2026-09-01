package com.mrleonardos.codeeconomy.internal.adapter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Осторожные обращения к чужому api.
 *
 * <p>
 * Ни класса, ни метода, ни значения может не быть, и ни один такой случай не должен ронять загрузку
 * мода. Поэтому здесь всё отвечает {@code null} вместо исключения, а решение, что делать с пустотой,
 * принимает вызывающий.
 */
final class Reflected {

    private Reflected() {}

    /** Класс по имени или {@code null}, если чужого мода на сервере нет. */
    static Class<?> type(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | LinkageError absent) {
            return null;
        }
    }

    /** Публичное поле или {@code null}. */
    static Field field(Class<?> owner, String name) {
        if (owner == null) {
            return null;
        }
        try {
            return owner.getField(name);
        } catch (NoSuchFieldException | LinkageError absent) {
            return null;
        }
    }

    /** Публичный метод или {@code null}. Ищется по объявляющему типу, а не по классу получателя. */
    static Method method(Class<?> owner, String name, Class<?>... parameters) {
        if (owner == null) {
            return null;
        }
        try {
            return owner.getMethod(name, parameters);
        } catch (NoSuchMethodException | LinkageError absent) {
            return null;
        }
    }

    /** Значение статического поля или {@code null}: чужой модуль мог ещё не подняться. */
    static Object value(Field field) {
        if (field == null) {
            return null;
        }
        try {
            return field.get(null);
        } catch (IllegalAccessException | RuntimeException | LinkageError refused) {
            return null;
        }
    }

    /** Вызов или {@code null}, если он не удался. */
    static Object call(Method method, Object target, Object... arguments) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, arguments);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return null;
        }
    }

    static long number(Object value, long fallback) {
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }

    static boolean flag(Object value, boolean fallback) {
        return value instanceof Boolean ? ((Boolean) value).booleanValue() : fallback;
    }

    static String text(Object value, String fallback) {
        if (!(value instanceof String)) {
            return fallback;
        }
        String text = ((String) value).trim();
        return text.isEmpty() ? fallback : text;
    }
}
