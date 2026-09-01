package com.mrleonardos.codeeconomy.api.event;

import java.util.List;

import com.mrleonardos.codeeconomy.api.model.TransactionRecord;

/**
 * Наблюдатель за экономикой.
 *
 * <p>
 * Слушатель ни на что не влияет: он для логов, кешей чужих модов и мостов. Все методы пустые по
 * умолчанию, поэтому реализуют только то, что нужно. Доставка идёт в главном потоке, слушатель видит
 * состояние, которое уже записано на носителе. Упавший слушатель пишется в лог и пропускается,
 * остальные получают событие.
 */
public interface EconomyListener {

    /** Итоговые изменения балансов за тик, уже записанные в журнал. */
    default void onBalanceChange(BalanceChange event) {}

    /** Записи журнала, для аудита и своих отчётов. */
    default void onTransactions(List<TransactionRecord> records) {}

    /** Вход или выход из режима деградации. */
    default void onDegraded(DegradedEvent event) {}

    /** Итог восстановления состояния при старте. */
    default void onRecovery(RecoveryEvent event) {}
}
