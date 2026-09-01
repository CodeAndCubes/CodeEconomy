package com.mrleonardos.codeeconomy.internal.adapter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.adapter.RoleAdapter;
import com.mrleonardos.codecore.api.adapter.RoleCapability;
import com.mrleonardos.codecore.api.adapter.RoleOwnerKind;
import com.mrleonardos.codecore.api.adapter.RoleServices;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codeeconomy.api.EconomyService;
import com.mrleonardos.codeeconomy.internal.EconomyRole;
import com.mrleonardos.codeeconomy.internal.EconomySection;
import com.mrleonardos.codeeconomy.internal.service.PlayerLookup;

/**
 * Заявка на деньги от ForgeEssentials.
 *
 * <p>
 * Отражение живёт внутри заявки: {@code available()} спрашивают у всех заявок роли, а {@code create()}
 * только у победителя, поэтому найденный мост запоминается и чужого api никто не касается, пока имя
 * {@code forgeessentials} не написано в {@code [owners]}.
 *
 * <p>
 * Заявка закрывает {@code balance} и {@code transfer}. Валют у чужого мода одна, журнала и топа нет
 * вовсе, поэтому {@code currencies}, {@code history} и {@code top} уходят в перечень недоступного, а не
 * притворяются работающими.
 */
public final class ForgeEssentialsAdapter implements RoleAdapter {

    /** Имя, которое админ пишет в {@code [owners] economy}. */
    public static final String NAME = "forgeessentials";

    private final Supplier<EconomySection> section;
    private final PlayerLookup lookup;
    private final Scheduler scheduler;
    private final Logger log;

    private ForgeEssentialsApi api;
    private boolean probed;

    public ForgeEssentialsAdapter(Supplier<EconomySection> section, PlayerLookup lookup, Scheduler scheduler,
        Logger log) {
        this.section = section;
        this.lookup = lookup;
        this.scheduler = scheduler;
        this.log = log;
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
        return RoleOwnerKind.ADAPTER;
    }

    /**
     * Стоит ли ForgeEssentials на сервере. Проверяются классы, а не значение
     * {@code APIRegistry.economy}: оно бывает пустым до подъёма чужого модуля, и отказать по нему значило
     * бы отдать роль другому из-за порядка загрузки.
     */
    @Override
    public boolean available() {
        return probe() != null;
    }

    @Override
    public Set<RoleCapability> capabilities() {
        return new HashSet<>(Arrays.asList(EconomyRole.BALANCE, EconomyRole.TRANSFER));
    }

    @Override
    public RoleServices create() {
        ForgeEssentialsApi found = probe();
        if (found == null) {
            throw new IllegalStateException("ForgeEssentials is not on this server, its offer must not have won");
        }
        log.warn(
            "Economy is served by ForgeEssentials: a transfer is a withdraw and an add without atomicity, "
                + "without an idempotency key and without a journal, so a crash between the two loses money");
        if (!found.ready()) {
            log.warn("ForgeEssentials economy is not up yet, its wallets answer as soon as its own module starts");
        }
        return RoleServices.builder()
            .add(EconomyService.class, new ForgeEssentialsEconomy(found, section, lookup, scheduler))
            .build();
    }

    private ForgeEssentialsApi probe() {
        if (!probed) {
            api = ForgeEssentialsApi.create();
            probed = true;
        }
        return api;
    }
}
