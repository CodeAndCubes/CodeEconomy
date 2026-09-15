package com.mrleonardos.codeeconomy.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.adapter.AdapterRegistry;
import com.mrleonardos.codecore.api.config.ConfigFile;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.config.ConfigService;
import com.mrleonardos.codecore.api.config.SectionSpec;
import com.mrleonardos.codecore.api.config.StorageSettings;
import com.mrleonardos.codecore.api.util.Scheduler;
import com.mrleonardos.codeeconomy.EconomyConstants;
import com.mrleonardos.codeeconomy.api.EconomyApi;
import com.mrleonardos.codeeconomy.api.EconomyLimits;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.api.store.EconomyStore;
import com.mrleonardos.codeeconomy.internal.adapter.ForgeEssentialsAdapter;
import com.mrleonardos.codeeconomy.internal.adapter.LedgerAdapter;
import com.mrleonardos.codeeconomy.internal.event.EventDispatcher;
import com.mrleonardos.codeeconomy.internal.service.LedgerService;
import com.mrleonardos.codeeconomy.internal.service.PlayerLookup;
import com.mrleonardos.codeeconomy.internal.store.Currencies;
import com.mrleonardos.codeeconomy.internal.store.JsonEconomyStore;

/**
 * Как мод входит в роль {@code economy} и что делает, когда она досталась другому.
 *
 * <p>
 * В фазе init объявляется секция {@code [economy]} главного файла, сама роль и обе заявки: наш леджер и
 * мост к ForgeEssentials. Своих файлов на этом шаге мод не открывает: реализация собирается в
 * {@code create()} у победителя, а победителя ядро выбирает в конце постинициализации.
 *
 * <p>
 * Роль ушла адаптеру или стоит {@code off}, и мод отходит целиком: ни команд, ни журнала, ни чекпоинта,
 * ни одного созданного файла. Существующие файлы при этом не трогаются, поэтому возврат владельца в
 * {@code [owners]} возвращает и деньги. Незнакомое имя провайдера в {@code [storage] provider}
 * выключает то же самое, но команды остаются и отвечают готовой строкой: экономика выключена.
 */
public final class EconomyBootstrap {

    private final ConfigService configs;
    private final Scheduler scheduler;
    private final BooleanSupplier mainThread;
    private final LongSupplier ticks;
    private final LongSupplier clock;
    private final PlayerLookup lookup;
    private final EventDispatcher events;
    private final Logger log;

    private ConfigFile<EconomySection> section;
    private ConfigFile<EconomySettings> settings;
    private LedgerAdapter own;
    private EconomyConfig config;
    private EconomyLimits limits;
    private String owner;

    public EconomyBootstrap(ConfigService configs, Scheduler scheduler, BooleanSupplier mainThread, LongSupplier ticks,
        LongSupplier clock, PlayerLookup lookup, EventDispatcher events, Logger log) {
        this.configs = configs;
        this.scheduler = scheduler;
        this.mainThread = mainThread;
        this.ticks = ticks;
        this.clock = clock;
        this.lookup = lookup;
        this.events = events;
        this.log = log;
    }

    /** Фаза init: секция главного файла, объявление роли и обе заявки на неё. */
    public void declare(AdapterRegistry adapters) {
        section = configs.section(
            SectionSpec.of(ConfigRoles.ECONOMY, EconomySection.class)
                .build());
        own = new LedgerAdapter(this::assemble);
        adapters.declareRole(EconomyRole.spec());
        adapters.offer(own);
        adapters.offer(new ForgeEssentialsAdapter(section::get, lookup, scheduler, log));
    }

    /**
     * Кто держит роль, и что мод делает дальше. Спрашивается после того, как ядро решило роли, и до
     * того, как оно отдаёт команды стартующему серверу.
     *
     * <p>
     * Незнакомое имя провайдера хранилища это тот же отход, но команды остаются и отвечают готовой
     * строкой о выключенной экономике: молчаливое «нет такой команды» тут запрещено так же, как и
     * молчаливый откат на встроенное хранилище.
     *
     * @param whenOwned корни команд, подписки на события мира и фоновый писатель: всё, что при чужом
     *                  владельце роли не должно появиться вовсе
     * @return правда ли мод работает сам
     */
    public boolean start(AdapterRegistry adapters, Consumer<LedgerService> whenOwned) {
        owner = adapters.owner(ConfigRoles.ECONOMY);
        if (!owns()) {
            log.info(
                "Role economy is held by {}, CodeEconomy stands down: no commands, no journal, no checkpoint, no files",
                owner == null ? "nobody" : owner);
            return false;
        }
        LedgerService assembled = own == null ? null : own.service();
        if (assembled == null) {
            return false;
        }
        log.info(
            "Role economy is held by {}, storage provider from the main config is {}",
            owner,
            config.provider());
        whenOwned.accept(assembled);
        return true;
    }

    /** Правда ли роль осталась за нашим модом. */
    public boolean owns() {
        return EconomyConstants.OWNER.equals(owner);
    }

    /** Правда ли отход случился из-за незнакомого имени провайдера, а не из-за роли. */
    public boolean stoodDownForStorage() {
        return owns() && own != null && own.service() == null;
    }

    /** Имя владельца роли или {@code null}, если роль не занята никем. */
    public String owner() {
        return owner;
    }

    /** Собранный леджер или {@code null}, если роль ушла другому. */
    public LedgerService service() {
        return own == null ? null : own.service();
    }

    /** Настройки денег целиком или {@code null}, если роль ушла другому. */
    public EconomyConfig config() {
        return config;
    }

    /** Потолки, посчитанные при сборке, или {@code null}, если роль ушла другому. */
    public EconomyLimits limits() {
        return limits;
    }

    /** Размер страницы топа и истории, перечитываемый вместе с файлом настроек. */
    public IntSupplier pageSize() {
        return () -> settings.get()
            .pageSize();
    }

    /**
     * Сборка леджера. Имя провайдера проверяется до открытия любого своего файла: незнакомое имя шва
     * выключает зависящее от него целиком, отката на встроенное хранилище нет, и выключенный мод не
     * оставляет после себя ни журнала, ни чекпоинта, ни пустых настроек.
     */
    private LedgerService assemble() {
        StorageSettings storage = configs.storage(ConfigRoles.ECONOMY);
        String provider = EconomyConfig.providerOf(storage);
        Optional<EconomyStore> foreign = EconomyApi.store(provider);
        if (!foreign.isPresent() && !JsonEconomyStore.ID.equals(provider)) {
            log.warn(
                "Storage seam [storage] provider names {}, which is not registered (registered: {}); "
                    + "the economy is switched off: no services, no journal, no files",
                provider,
                registeredIds());
            return null;
        }
        settings = configs.open(EconomySettings.spec());
        config = EconomyConfig.of(settings.get(), section.get(), storage, configs.audit(ConfigRoles.ECONOMY));
        limits = config.ceilings(log);
        List<CurrencyRecord> currencies = Currencies.load(
            configs.open(Currencies.spec())
                .get(),
            limits,
            log);
        EconomyStore store = foreign.isPresent() ? foreign.get()
            : new JsonEconomyStore(
                configs.open(JsonEconomyStore.spec()),
                limits,
                Currencies.startBalances(currencies),
                config.idempotencyMillis(),
                clock,
                log);
        return LedgerService.create(
            store,
            config,
            currencies,
            scheduler,
            mainThread,
            ticks,
            clock,
            lookup,
            events,
            log);
    }

    private static List<String> registeredIds() {
        List<String> ids = new ArrayList<>();
        for (EconomyStore store : EconomyApi.stores()) {
            ids.add(store.id());
        }
        return ids;
    }
}
