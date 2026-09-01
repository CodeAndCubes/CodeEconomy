package com.mrleonardos.codeeconomy.internal.adapter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import com.mrleonardos.codecore.api.adapter.RoleAdapter;
import com.mrleonardos.codecore.api.adapter.RoleCapability;
import com.mrleonardos.codecore.api.adapter.RoleOwnerKind;
import com.mrleonardos.codecore.api.adapter.RoleServices;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codeeconomy.api.EconomyService;
import com.mrleonardos.codeeconomy.internal.EconomyConstants;
import com.mrleonardos.codeeconomy.internal.EconomyRole;
import com.mrleonardos.codeeconomy.internal.service.LedgerService;

/**
 * Заявка самого CodeEconomy: леджер с журналом закрывает роль целиком.
 *
 * <p>
 * Реализация собирается в {@link #create()}, то есть только когда роль досталась нам. Проиграв роль, мод
 * не открывает ни одного своего файла и не заводит писателя: плодить пустой журнал рядом с чужой
 * экономикой значит запутывать админа.
 */
public final class LedgerAdapter implements RoleAdapter {

    /** Имя, которое админ пишет в {@code [owners] economy}. */
    public static final String NAME = EconomyConstants.OWNER;

    private final Supplier<LedgerService> factory;

    private LedgerService service;

    public LedgerAdapter(Supplier<LedgerService> factory) {
        this.factory = factory;
    }

    @Override
    public String role() {
        return ConfigRoles.ECONOMY;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public RoleOwnerKind kind() {
        return RoleOwnerKind.MOD;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public Set<RoleCapability> capabilities() {
        return new HashSet<>(
            Arrays.asList(
                EconomyRole.BALANCE,
                EconomyRole.TRANSFER,
                EconomyRole.CURRENCIES,
                EconomyRole.TOP,
                EconomyRole.HISTORY));
    }

    @Override
    public RoleServices create() {
        service = factory.get();
        return RoleServices.builder()
            .add(EconomyService.class, service)
            .build();
    }

    /** Собранный леджер или {@code null}, если роль ушла другому. */
    public LedgerService service() {
        return service;
    }
}
