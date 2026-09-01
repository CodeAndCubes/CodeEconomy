package com.mrleonardos.codeeconomy.internal;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Значение, которое считают по первому обращению и запоминают.
 *
 * <p>
 * Нужно там, где ответ зависит от фазы загрузки: реестры чужих модов открыты всю инициализацию, а
 * посчитанное в свой init значение застаёт половину сервера несобранной. Ленивое чтение снимает
 * зависимость от порядка загрузки модов, и это дешевле, чем угадывать фазу.
 */
public final class Lazy<T> {

    private final Supplier<T> source;
    private volatile T value;

    private Lazy(Supplier<T> source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    public static <T> Lazy<T> of(Supplier<T> source) {
        return new Lazy<>(source);
    }

    /** Значение, считая его при первом обращении. */
    public T get() {
        T known = value;
        if (known != null) {
            return known;
        }
        synchronized (this) {
            if (value == null) {
                value = Objects.requireNonNull(source.get(), "value");
            }
            return value;
        }
    }

    /** Уже посчитанное значение или null: чтобы остановка не поднимала то, чем ни разу не пользовались. */
    public T peek() {
        return value;
    }
}
