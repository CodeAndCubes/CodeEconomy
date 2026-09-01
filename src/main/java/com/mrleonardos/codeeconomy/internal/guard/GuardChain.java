package com.mrleonardos.codeeconomy.internal.guard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codeeconomy.api.guard.GuardResult;
import com.mrleonardos.codeeconomy.api.guard.TransferGuard;
import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.TransferRequest;

/**
 * Цепочка гвардов: правила отказа до записи.
 *
 * <p>
 * Порядок задаёт числовой приоритет, при равенстве порядок регистрации. Первый отказ останавливает
 * цепочку, остальные гварды не вызываются. Упавший гвард при {@code guards.failOpen = false} ветит
 * операцию, при true пропускается, в обоих случаях имя гварда попадает в лог.
 */
public final class GuardChain {

    private final List<TransferGuard> guards;
    private final boolean failOpen;
    private final Logger log;

    private GuardChain(List<TransferGuard> guards, boolean failOpen, Logger log) {
        this.guards = guards;
        this.failOpen = failOpen;
        this.log = log;
    }

    /** Цепочка из перечня в порядке регистрации, отсортированного по приоритету. */
    public static GuardChain of(List<TransferGuard> registered, boolean failOpen, Logger log) {
        List<TransferGuard> sorted = new ArrayList<>(registered);
        sorted.sort(ORDER);
        return new GuardChain(Collections.unmodifiableList(sorted), failOpen, log);
    }

    /** Гварды в порядке вызова. */
    public List<TransferGuard> guards() {
        return guards;
    }

    /**
     * Прогнать запрос через цепочку.
     *
     * @return отказ или пустой ответ, когда операцию пускают
     */
    public Optional<Veto> check(TransferRequest request, AccountView from, AccountView to) {
        for (TransferGuard guard : guards) {
            GuardResult result;
            try {
                result = guard.check(request, from, to);
            } catch (RuntimeException failure) {
                if (log != null) {
                    log.warn("Guard {} failed: {}", guard.id(), failure.toString());
                }
                if (failOpen) {
                    continue;
                }
                return Optional.of(new Veto(guard.id(), "guard failed: " + failure.getMessage()));
            }
            if (result != null && !result.allowed()) {
                return Optional.of(
                    new Veto(
                        guard.id(),
                        result.reason()
                            .orElse("no reason given")));
            }
        }
        return Optional.empty();
    }

    /**
     * Сообщить цепочке о записанной операции: гварды отмечают кулдауны и квоты. Упавший гвард попадает
     * в лог, остальные всё равно получают уведомление.
     */
    public void committed(TransferRequest request) {
        for (TransferGuard guard : guards) {
            try {
                guard.committed(request);
            } catch (RuntimeException failure) {
                if (log != null) {
                    log.warn("Guard {} failed after the write: {}", guard.id(), failure.toString());
                }
            }
        }
    }

    private static final Comparator<TransferGuard> ORDER = new Comparator<TransferGuard>() {

        @Override
        public int compare(TransferGuard left, TransferGuard right) {
            return Integer.compare(left.priority(), right.priority());
        }
    };

    /** Отказ цепочки: кто ветил и почему. */
    public static final class Veto {

        private final String guardId;
        private final String reason;

        Veto(String guardId, String reason) {
            this.guardId = guardId;
            this.reason = reason;
        }

        /** Имя гварда. */
        public String guardId() {
            return guardId;
        }

        /** Причина отказа. */
        public String reason() {
            return reason;
        }

        @Override
        public String toString() {
            return guardId + ": " + reason;
        }
    }
}
