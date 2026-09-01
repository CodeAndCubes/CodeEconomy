package com.mrleonardos.codeeconomy.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

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
 * Хранилища и гварды регистрируют на инициализации своего мода: реестр закрывается в постинициализации
 * CodeEconomy, и только после этого выбирается активный провайдер и собирается цепочка гвардов.
 * Регистрация после заморозки отклоняется {@code IllegalStateException}.
 *
 * <p>
 * {@link #service()} отвечает тем же, чем реестр сервисов ядра: если экономику подменил другой мод,
 * эта дверь ведёт к нему, а не к реализации CodeEconomy. Обращение до того, как CodeEconomy поднялся,
 * даёт понятную ошибку, а не падение.
 */
public final class EconomyApi {

    private static final Map<String, EconomyStore> STORES = new LinkedHashMap<>();

    private static final Map<String, TransferGuard> GUARDS = new LinkedHashMap<>();

    private static volatile boolean frozen;

    private static volatile Supplier<EconomyService> service;

    private static volatile EconomyEvents events;

    private EconomyApi() {}

    /**
     * Зарегистрировать хранилище. Провайдер с уже занятым именем заменяет прежнего, активным становится
     * тот, чьё имя стоит в {@code [storage] provider} главного файла линейки.
     *
     * @throws IllegalStateException если реестр уже заморожен
     */
    public static synchronized void registerStore(EconomyStore store) {
        Objects.requireNonNull(store, "store");
        ensureOpen();
        STORES.put(store.id(), store);
    }

    /** Хранилище по имени из {@code [storage] provider} главного файла. */
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

    /**
     * Подключает точку входа. Вызывается самим CodeEconomy: чужим модам метод не нужен.
     *
     * @param registryService откуда брать сервис; это реестр ядра, а не поле с реализацией, чтобы
     *                        подмена сервиса чужим модом была видна и через эту дверь
     */
    public static void install(Supplier<EconomyService> registryService, EconomyEvents installedEvents) {
        service = Objects.requireNonNull(registryService, "registryService");
        events = Objects.requireNonNull(installedEvents, "installedEvents");
    }

    /** Деньги сервера: та же реализация, что держит реестр сервисов ядра. */
    public static EconomyService service() {
        Supplier<EconomyService> installed = service;
        if (installed == null) {
            throw new IllegalStateException("CodeEconomy is not ready yet, call it no earlier than its init phase");
        }
        EconomyService held = installed.get();
        if (held == null) {
            throw new IllegalStateException("No mod holds EconomyService in the core registry");
        }
        return held;
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
