package com.mrleonardos.codeeconomy.internal;

import java.util.Objects;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codecore.api.config.AuditSettings;
import com.mrleonardos.codecore.api.config.StorageSettings;
import com.mrleonardos.codeeconomy.api.EconomyLimits;
import com.mrleonardos.codeeconomy.internal.store.JsonEconomyStore;

/**
 * Настройки денег, собранные из четырёх источников: собственного файла мода, секции {@code [economy]}
 * главного файла и общих для линейки {@code [storage]} с {@code [audit]} вместе с перекрытием по роли.
 *
 * <p>
 * Движок спрашивает значения только здесь и не знает, из какого файла каждое пришло. Перекрытие по роли
 * разбирает ядро, своей копии разбора у мода нет.
 */
public final class EconomyConfig {

    private final EconomySettings settings;
    private final EconomySection section;
    private final StorageSettings storage;
    private final AuditSettings audit;

    private EconomyConfig(EconomySettings settings, EconomySection section, StorageSettings storage,
        AuditSettings audit) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.section = Objects.requireNonNull(section, "section");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    public static EconomyConfig of(EconomySettings settings, EconomySection section, StorageSettings storage,
        AuditSettings audit) {
        return new EconomyConfig(settings, section, storage, audit);
    }

    /** Имя провайдера хранилища из главного файла, пустое значение считается встроенным json. */
    public String provider() {
        return providerOf(storage);
    }

    /** Нормализованное имя провайдера: им пользуется и сборка до открытия настроек, не только конфиг. */
    public static String providerOf(StorageSettings storage) {
        String named = storage.provider();
        return named == null || named.trim()
            .isEmpty() ? JsonEconomyStore.ID : named.trim();
    }

    /** Через сколько тиков пишется чекпоинт. */
    public int autosaveTicks() {
        return Math.max(1, storage.autosaveSeconds()) * 20;
    }

    /** Валюта, которую подставляют команды без явного имени. */
    public String currencyId() {
        return section.currencyId();
    }

    public long minTransfer() {
        return section.minTransfer;
    }

    public long maxTransfer() {
        return section.maxTransfer;
    }

    public int payCooldownSeconds() {
        return section.payCooldownSeconds;
    }

    public boolean logChanges() {
        return audit.logChanges();
    }

    public boolean logChecks() {
        return audit.logChecks();
    }

    public boolean failOpen() {
        return settings.guards.failOpen;
    }

    public int cacheTicks() {
        return settings.top.cacheTicks;
    }

    public int pageSize() {
        return settings.pageSize();
    }

    public long idempotencyMillis() {
        return settings.idempotencyMillis();
    }

    public long historyMillis() {
        return settings.historyMillis();
    }

    public EconomyLimits ceilings(Logger log) {
        return settings.ceilings(log);
    }
}
