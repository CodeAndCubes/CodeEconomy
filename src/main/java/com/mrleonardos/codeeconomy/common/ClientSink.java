package com.mrleonardos.codeeconomy.common;

/**
 * То, что приходящие пакеты делают на клиенте.
 *
 * <p>
 * Пакеты общие для обеих сторон, а показ баланса существует только в клиентском jar. Поэтому
 * обработчик пакета обращается сюда, а не к рисованию напрямую: общий код не должен ссылаться на
 * вырезаемые классы.
 */
public interface ClientSink {

    /**
     * Сервер прислал баланс.
     *
     * @param currencyId идентификатор валюты
     * @param amount     сумма в минорных единицах
     * @param decimals   сколько младших знаков показывать человеку
     */
    void balance(String currencyId, long amount, int decimals);
}
