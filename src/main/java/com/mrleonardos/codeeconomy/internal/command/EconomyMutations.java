package com.mrleonardos.codeeconomy.internal.command;

import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.TransferRequest;
import com.mrleonardos.codeeconomy.api.model.TransferResult;

/**
 * Шов команд в конвейер денег.
 *
 * <p>
 * Команда обязана оставить в записи причину {@code COMMAND}, а не {@code API}, поэтому в конвейер она
 * идёт мимо {@code EconomyService}. Здесь же команда сообщает, есть ли у отправителя право уводить счёт
 * ниже пола: у консоли нет uuid, и спросить о ней ноду умеет только шов команд.
 */
public interface EconomyMutations {

    /**
     * Провести операцию.
     *
     * @param floorBypass право {@code codeeconomy.bypass.minbalance} у отправителя команды
     */
    TransferResult apply(TransferRequest request, ChangeCause cause, boolean floorBypass);
}
