package com.mrleonardos.codeeconomy.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.mrleonardos.codeeconomy.api.event.EconomyEvents;
import com.mrleonardos.codeeconomy.api.guard.TransferGuard;
import com.mrleonardos.codeeconomy.api.store.EconomyStore;

/**
 * Точка входа для чужих модов.
 *
 * <pre>
 *
 * EconomyApi.registerStore(new SqlStore());
 * EconomyApi.registerGuard(new ShopEscrowGuard());
 * </pre>
 *
 * <p>
 * Хранилища и гварды регистрируют на инициализации своего мода: к первому использованию денег всё
 * должно быть на местах. После заморозки реестра регистрация отклоняется {@code IllegalStateException},
 * потому что выбор провайдера и порядок цепочки уже состоялся. {@link #service()} и {@link #events()}
 * работают после того, как CodeEconomy соберёт свою реализацию, обращение раньше даёт понятную ошибку,
 * а не падение.
 */
public final class EconomyApi {

    private static final Map<String, EconomyStore> STORES = new LinkedHashMap<>();

    private static final Map<String, TransferGuard> GUARDS = new LinkedHashMap<>();

    private static volatile boolean frozen;

    private static volatile EconomyService service;

    private static volatile EconomyEvents events;

    private EconomyApi() {}

    /**
     * Зарегистрировать хранилище. Провайдер с уже занятым именем заменяет прежнего, активным становится
     * тот, чьё имя указано в настройке {@code storage.provider}.
     *
     * @throws IllegalStateException если реестр уже заморожен
     */
    public static synchronized void registerStore(EconomyStore store) {
        Objects.requireNonNull(store, "store");
        ensureOpen();
        STORES.put(store.id(), store);
    }

    /** Хранилище по имени из настройки {@code storage.provider}. */
    public static synchronized Optional<EconomyStore> store(String id) {
        return Optional.ofNullable(STORES.get(Objects.requireNonNull(id, "id")));
    }

    /** Все зарегистрированные хранилища в порядке регистрации. */
    public static synchronized List<EconomyStore> stores() {
        return new ArrayList<>(STORES.values());
    }

    /**
     * Зарегистрировать гвард. Гвард с уже занятым именем заменяет прежнего и занимает его место в
     * цепочке.
     *
     * @throws IllegalStateException если реестр уже заморожен
     */
    public static synchronized void registerGuard(TransferGuard guard) {
        Objects.requireNonNull(guard, "guard");
        ensureOpen();
        GUARDS.put(guard.id(), guard);
    }

    /** Все зарегистрированные гварды в порядке регистрации, порядок в цепочке задаёт приоритет. */
    public static synchronized List<TransferGuard> guards() {
        return new ArrayList<>(GUARDS.values());
    }

    /** Закрыть реестр для новых хранилищ и гвардов. Вызывается самим CodeEconomy. */
    public static synchronized void freeze() {
        frozen = true;
    }

    /** Подключает реализацию. Вызывается самим CodeEconomy: чужим модам метод не нужен. */
    public static void install(EconomyService installedService, EconomyEvents installedEvents) {
        service = Objects.requireNonNull(installedService, "installedService");
        events = Objects.requireNonNull(installedEvents, "installedEvents");
    }

    /** Деньги сервера. */
    public static EconomyService service() {
        EconomyService installed = service;
        if (installed == null) {
            throw new IllegalStateException("CodeEconomy is not ready yet, call it no earlier than its init phase");
        }
        return installed;
    }

    /** Реестр слушателей экономики. */
    public static EconomyEvents events() {
        EconomyEvents installed = events;
        if (installed == null) {
            throw new IllegalStateException("CodeEconomy is not ready yet, call it no earlier than its init phase");
        }
        return installed;
    }

    private static void ensureOpen() {
        if (frozen) {
            throw new IllegalStateException("CodeEconomy registry is frozen, register stores and guards in init");
        }
    }
}
