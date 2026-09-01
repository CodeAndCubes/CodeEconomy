package com.mrleonardos.codeeconomy.api.event;

import java.util.List;

/**
 * Реестр слушателей экономики.
 *
 * <p>
 * Порядок задаётся числом: меньшее значит более ранний вызов. При равном приоритете слушатели идут в
 * порядке регистрации, поэтому поведение не зависит от порядка загрузки модов. Регистрацию делают на
 * инициализации своего мода.
 */
public interface EconomyEvents {

    /**
     * Добавить слушателя.
     *
     * @param priority меньшее число означает более ранний вызов
     */
    void register(int priority, EconomyListener listener);

    /** Убрать слушателя. */
    void unregister(EconomyListener listener);

    /** Слушатели в порядке вызова. */
    List<EconomyListener> listeners();
}
