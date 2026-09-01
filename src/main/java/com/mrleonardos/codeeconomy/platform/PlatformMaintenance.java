package com.mrleonardos.codeeconomy.platform;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codeeconomy.api.EconomyLimits;
import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.api.model.ResultCode;
import com.mrleonardos.codeeconomy.api.model.TransferRequest;
import com.mrleonardos.codeeconomy.api.store.StoreResult;
import com.mrleonardos.codeeconomy.internal.command.EconomyMaintenance;
import com.mrleonardos.codeeconomy.internal.command.EconomyMessages;
import com.mrleonardos.codeeconomy.internal.command.MaintenanceOutcome;
import com.mrleonardos.codeeconomy.internal.service.LedgerService;
import com.mrleonardos.codeeconomy.internal.store.BalanceImporters;
import com.mrleonardos.codeeconomy.internal.store.Recovery;

final class PlatformMaintenance implements EconomyMaintenance {

    private static final String IMPORT_PREFIX = "import:";

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
            Recovery.Verification verification = service.verifyDetailed();
            for (String finding : verification.findings()) {
                log.warn("Economy verify: {}", finding);
            }
            if (verification.skipped() > 0L) {
                log.info(
                    "Economy verify skipped {} journal line(s) at or below the checkpoint, their balances are in the checkpoint",
                    verification.skipped());
            }
            if (verification.findings()
                .isEmpty()) {
                log.info("Economy verify finished, the journal and the accounts agree");
            } else {
                log.warn(
                    "Economy verify finished with {} finding(s), first: {}",
                    verification.findings()
                        .size(),
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
        StoreResult stored = service.checkpoint();
        return stored.successful() ? MaintenanceOutcome.success(
            service.ledger()
                .state()
                .checkpointSeq())
            : MaintenanceOutcome.failure(ResultCode.STORE_FAILURE);
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
        BalanceImporters.Imported imported = BalanceImporters.read(format, resolve(file), currency, limits);
        long moved = applyImport(format, resolve(file), currency, imported, apply);
        MaintenanceOutcome outcome = MaintenanceOutcome.success(
            0L,
            rows(imported),
            Long.valueOf(moved),
            Long.valueOf(
                imported.rejected()
                    .size()));
        return outcome;
    }

    private long applyImport(String format, Path source, CurrencyRecord currency, BalanceImporters.Imported imported,
        boolean apply) {
        long moved = 0L;
        for (java.util.Map.Entry<UUID, Long> entry : imported.accepted()
            .entrySet()) {
            if (!apply) {
                moved++;
                continue;
            }
            TransferRequest request = TransferRequest.set(
                entry.getKey(),
                entry.getValue()
                    .longValue(),
                currency.id(),
                IMPORT_PREFIX + currency.id() + ":" + entry.getKey(),
                null,
                "imported " + format + " from " + source.getFileName());
            if (service.ledger()
                .execute(request, ChangeCause.MIGRATION)
                .applied()) {
                moved++;
            }
        }
        return moved;
    }

    private List<MaintenanceOutcome.Row> rows(BalanceImporters.Imported imported) {
        List<MaintenanceOutcome.Row> rows = new ArrayList<>();
        for (String rejection : imported.rejected()) {
            rows.add(MaintenanceOutcome.Row.of(EconomyMessages.IMPORT_SKIP, rejection));
        }
        return rows;
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
