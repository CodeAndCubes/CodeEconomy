package com.mrleonardos.codeeconomy.api.guard;

import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.TransferRequest;

/**
 * Правило отказа до записи.
 *
 * <p>
 * Гвард смотрит на запрос и счета и решает, пускать ли операцию. У записи ещё нет: гвард отвечает до
 * обращения к хранилищу, поэтому его отказ не оставляет следов ни в журнале, ни в событиях. Отменить
 * операцию после записи гвард не может, для уведомлений служат события.
 *
 * <p>
 * Встроенные гварды это личный потолок одного перевода из меты {@code codeeconomy.paylimit} и
 * кулдаун {@code /pay}. Свой гвард чужой мод регистрирует в {@code EconomyApi} на инициализации.
 */
public interface TransferGuard {

    /** Имя гварда, по нему он виден в логе, в том числе когда упал. */
    String id();

    /** Порядок в цепочке, меньшее число означает более ранний вызов. */
    int priority();

    /**
     * Проверить операцию.
     *
     * @param request что собираются сделать
     * @param from    счёт отправителя или null, когда у операции этой стороны нет
     * @param to      счёт получателя или null, когда у операции этой стороны нет
     */
    GuardResult check(TransferRequest request, AccountView from, AccountView to);
}
