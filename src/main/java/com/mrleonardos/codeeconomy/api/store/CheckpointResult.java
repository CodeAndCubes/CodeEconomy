package com.mrleonardos.codeeconomy.api.store;

import java.util.Objects;

/**
 * Итог принудительного снимка: записан ли он и до какой записи доведён.
 *
 * <p>
 * Граница нужна вызывающему, чтобы ответить администратору числом, а не догадкой: до сих пор
 * {@code /eco checkpoint} печатал границу из состояния движка, которую снимок не двигал.
 */
public final class CheckpointResult {

    private final StoreResult result;
    private final boolean written;
    private final long seq;

    private CheckpointResult(StoreResult result, boolean written, long seq) {
        this.result = result;
        this.written = written;
        this.seq = seq;
    }

    /**
     * Снимок записан.
     *
     * @param seq граница, до которой доведён чекпоинт
     */
    public static CheckpointResult written(long seq) {
        return new CheckpointResult(StoreResult.success(), true, seq);
    }

    /**
     * Писать было нечего: с прошлого снимка счета не менялись.
     *
     * @param seq граница, на которой стоит чекпоинт
     */
    public static CheckpointResult upToDate(long seq) {
        return new CheckpointResult(StoreResult.success(), false, seq);
    }

    /** Снимок не состоялся, причина внутри. */
    public static CheckpointResult failure(StoreResult failure) {
        Objects.requireNonNull(failure, "failure");
        if (failure.successful()) {
            throw new IllegalArgumentException("Checkpoint failure needs a refusal, not a success");
        }
        return new CheckpointResult(failure, false, 0L);
    }

    /** Ответ носителя. */
    public StoreResult result() {
        return result;
    }

    /** Правда ли снимок состоялся: отказа не было. */
    public boolean successful() {
        return result.successful();
    }

    /** Правда ли файл действительно переписан. */
    public boolean written() {
        return written;
    }

    /** Граница, до которой доведён чекпоинт. */
    public long seq() {
        return seq;
    }

    @Override
    public String toString() {
        if (!successful()) {
            return result.toString();
        }
        return (written ? "written up to " : "already at ") + seq;
    }
}
