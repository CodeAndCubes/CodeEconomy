package com.mrleonardos.codeeconomy.platform;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codeeconomy.api.Amounts;
import com.mrleonardos.codeeconomy.api.EconomyLimits;
import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.api.model.ResultCode;
import com.mrleonardos.codeeconomy.api.model.TransferRequest;
import com.mrleonardos.codeeconomy.api.model.TransferResult;
import com.mrleonardos.codeeconomy.api.store.CheckpointResult;
import com.mrleonardos.codeeconomy.api.store.StoreResult;
import com.mrleonardos.codeeconomy.api.store.StoreVerification;
import com.mrleonardos.codeeconomy.internal.command.EconomyMaintenance;
import com.mrleonardos.codeeconomy.internal.command.EconomyMessages;
import com.mrleonardos.codeeconomy.internal.command.MaintenanceOutcome;
import com.mrleonardos.codeeconomy.internal.service.LedgerService;
import com.mrleonardos.codeeconomy.internal.store.BalanceImporters;

final class PlatformMaintenance implements EconomyMaintenance {

    private static final String IMPORT_PREFIX = "import:";

    /** Сколько принятых строк показать в отчёте без {@code --apply}: длиннее чат всё равно не примет. */
    private static final int PREVIEW_ROWS = 20;

    private final LedgerService service;
    private final EconomyLimits limits;
    private final Supplier<Path> sources;
    private final Logger log;
    private final ExecutorService verifier;

    PlatformMaintenance(LedgerService service, EconomyLimits limits, Supplier<Path> sources, Logger log) {
        this.service = service;
        this.limits = limits;
        this.sources = sources;
        this.log = log;
        this.verifier = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "CodeEconomy-Verify");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public MaintenanceOutcome freeze(UUID player, boolean frozen, UUID actor, String transactionId) {
        MaintenanceOutcome refused = guard(player);
        if (refused != null) {
            return refused;
        }
        StoreResult stored = service.ledger()
            .setFrozen(player, frozen);
        if (!stored.successful()) {
            return MaintenanceOutcome.failure(ResultCode.STORE_FAILURE);
        }
        log.info(
            "Economy freeze {} for {}, actor {}, tx {}",
            frozen ? "on" : "off",
            player,
            actor == null ? "console" : actor.toString(),
            transactionId);
        return MaintenanceOutcome.success(0L);
    }

    @Override
    public MaintenanceOutcome verify() {
        verifier.execute(() -> {
            StoreVerification verification = service.verifyDetailed();
            for (String finding : verification.findings()) {
                log.warn("Economy verify: {}", finding);
            }
            if (verification.settled() > 0L) {
                log.info(
                    "Economy verify skipped {} journal line(s) at or below the checkpoint, their balances are in the checkpoint",
                    Long.valueOf(verification.settled()));
            }
            if (verification.ahead() > 0L) {
                log.info(
                    "Economy verify skipped {} journal line(s) written while it was running, they are past the snapshot",
                    Long.valueOf(verification.ahead()));
            }
            if (verification.findings()
                .isEmpty()) {
                log.info("Economy verify finished, the journal and the accounts agree");
            } else {
                log.warn(
                    "Economy verify finished with {} finding(s), first: {}",
                    Integer.valueOf(
                        verification.findings()
                            .size()),
                    verification.findings()
                        .get(0));
            }
        });
        return MaintenanceOutcome.success(0L);
    }

    @Override
    public MaintenanceOutcome checkpoint() {
        MaintenanceOutcome refused = guard();
        if (refused != null) {
            return refused;
        }
        CheckpointResult written = service.checkpoint();
        if (!written.successful()) {
            return MaintenanceOutcome.failure(ResultCode.STORE_FAILURE);
        }
        return MaintenanceOutcome.success(written.seq(), Boolean.valueOf(written.written()));
    }

    @Override
    public MaintenanceOutcome compact() {
        MaintenanceOutcome refused = guard();
        if (refused != null) {
            return refused;
        }
        StoreResult stored = service.compact();
        return stored.successful() ? MaintenanceOutcome.success(
            service.ledger()
                .state()
                .checkpointSeq())
            : MaintenanceOutcome.failure(ResultCode.STORE_FAILURE);
    }

    @Override
    public MaintenanceOutcome unlock() {
        if (!service.readOnly()) {
            return MaintenanceOutcome.success(0L);
        }
        StoreResult lifted = service.unlock();
        if (!lifted.successful()) {
            return MaintenanceOutcome.failure(ResultCode.STORE_FAILURE);
        }
        return MaintenanceOutcome.success(1L);
    }

    @Override
    public MaintenanceOutcome importBalances(String format, String file, boolean apply) {
        MaintenanceOutcome refused = guard();
        if (refused != null) {
            return refused;
        }
        if (!BalanceImporters.knows(format)) {
            return MaintenanceOutcome.failure(ResultCode.INVALID_REQUEST);
        }
        CurrencyRecord currency = service.currency(service.defaultCurrencyId())
            .orElse(null);
        if (currency == null) {
            return MaintenanceOutcome.failure(ResultCode.UNKNOWN_CURRENCY);
        }
        Path source = resolve(file);
        BalanceImporters.Imported imported = BalanceImporters.read(format, source, currency, limits);
        List<MaintenanceOutcome.Row> rows = new ArrayList<>();
        long moved = applyImport(format, source, currency, imported, apply, rows);
        preview(imported, currency, apply, rows);
        for (String rejection : imported.rejected()) {
            rows.add(MaintenanceOutcome.Row.of(EconomyMessages.IMPORT_SKIP, rejection));
        }
        return MaintenanceOutcome.success(
            0L,
            rows,
            Long.valueOf(moved),
            Long.valueOf(
                imported.rejected()
                    .size()));
    }

    private long applyImport(String format, Path source, CurrencyRecord currency, BalanceImporters.Imported imported,
        boolean apply, List<MaintenanceOutcome.Row> rows) {
        if (!apply) {
            return imported.accepted()
                .size();
        }
        String reason = reason(format, source);
        long moved = 0L;
        for (Map.Entry<UUID, Long> entry : imported.accepted()
            .entrySet()) {
            TransferRequest request = TransferRequest.set(
                entry.getKey(),
                entry.getValue()
                    .longValue(),
                currency.id(),
                IMPORT_PREFIX + currency.id() + ":" + entry.getKey(),
                null,
                reason);
            TransferResult result = service.ledger()
                .execute(request, ChangeCause.MIGRATION);
            if (result.applied()) {
                moved++;
                continue;
            }
            rows.add(MaintenanceOutcome.Row.of(EconomyMessages.IMPORT_SKIP, entry.getKey() + ": " + result.code()));
        }
        return moved;
    }

    /**
     * Причина записи журнала для импорта, обрезанная под потолок причины. Без обрезки длинное имя
     * каталога отклоняло бы каждую строку кодом INVALID_REQUEST, и отчёт показывал бы ноль без
     * объяснения.
     */
    private String reason(String format, Path source) {
        String reason = "imported " + format + " from " + source.getFileName();
        return reason.length() <= limits.reasonLength() ? reason : reason.substring(0, limits.reasonLength());
    }

    /** Построчный показ принятых пар: без него отчёт сухого прогона нечем проверить. */
    private static void preview(BalanceImporters.Imported imported, CurrencyRecord currency, boolean apply,
        List<MaintenanceOutcome.Row> rows) {
        if (apply) {
            return;
        }
        int shown = 0;
        for (Map.Entry<UUID, Long> entry : imported.accepted()
            .entrySet()) {
            if (shown >= PREVIEW_ROWS) {
                rows.add(
                    MaintenanceOutcome.Row.of(
                        EconomyMessages.IMPORT_MORE,
                        Integer.valueOf(
                            imported.accepted()
                                .size() - shown)));
                return;
            }
            rows.add(
                MaintenanceOutcome.Row.of(
                    EconomyMessages.IMPORT_ROW,
                    entry.getKey(),
                    Amounts.format(
                        entry.getValue()
                            .longValue(),
                        currency)));
            shown++;
        }
    }

    private Path resolve(String file) {
        String name = file == null || file.trim()
            .isEmpty() ? "" : file.trim();
        Path given = Paths.get(name);
        if (given.isAbsolute()) {
            return given;
        }
        return sources.get()
            .resolve(given);
    }

    private MaintenanceOutcome guard() {
        if (service.readOnly()) {
            return MaintenanceOutcome.failure(ResultCode.READONLY);
        }
        return null;
    }

    private MaintenanceOutcome guard(UUID player) {
        if (service.readOnly()) {
            return MaintenanceOutcome.failure(ResultCode.READONLY);
        }
        if (player == null || !service.ledger()
            .state()
            .account(player)
            .isPresent()) {
            return MaintenanceOutcome.failure(ResultCode.UNKNOWN_PLAYER);
        }
        return null;
    }
}
