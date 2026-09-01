package com.mrleonardos.codeeconomy.internal.command;

import java.util.UUID;

public interface EconomyMaintenance {

    MaintenanceOutcome freeze(UUID player, boolean frozen, UUID actor, String transactionId);

    MaintenanceOutcome verify();

    MaintenanceOutcome checkpoint();

    MaintenanceOutcome compact();

    /** Снять карантин носителя: после разбора администратором мутации открываются снова. */
    MaintenanceOutcome unlock();

    MaintenanceOutcome importBalances(String format, String file, boolean apply);
}
