package com.mrleonardos.codeeconomy.internal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mrleonardos.codeeconomy.api.CurrencyIds;
import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.ChangeCause;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;
import com.mrleonardos.codeeconomy.api.model.TransferRequest;
import com.mrleonardos.codeeconomy.api.store.ChangeBatch;
import com.mrleonardos.codeeconomy.api.store.EconomyStore;
import com.mrleonardos.codeeconomy.api.store.StoreResult;
import com.mrleonardos.codeeconomy.api.store.StoreSnapshot;
import com.mrleonardos.codeeconomy.internal.engine.Ledger;
import com.mrleonardos.codeeconomy.internal.event.EventDispatcher;
import com.mrleonardos.codeeconomy.internal.guard.GuardChain;
import com.mrleonardos.codeeconomy.internal.service.PlayerLookup;
import com.mrleonardos.codeeconomy.internal.store.JsonEconomyStore;

/** Общие предметы тестов: валюты, игроки, подставной провайдер и собранный движок. */
public final class EconomyFixtures {

    public static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    public static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    public static final UUID CAROL = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    public static final Logger LOG = LogManager.getLogger("economy-tests");

    private EconomyFixtures() {}

    public static CurrencyRecord coin() {
        return CurrencyRecord.defaultCoin();
    }

    /** Валюта с дном ниже нуля: для проверок обхода пола и отрицательных установок. */
    public static CurrencyRecord credit() {
        return CurrencyRecord.builder("credit")
            .displayName("Credit")
            .symbol("c")
            .decimals(0)
            .startBalance(100L)
            .minBalance(0L)
            .negativeFloor(-500L)
            .maxBalance(10000L)
            .payAllowed(true)
            .visible(true)
            .build();
    }

    public static List<CurrencyRecord> currencies(CurrencyRecord... records) {
        return Collections.unmodifiableList(java.util.Arrays.asList(records));
    }

    public static EconomySettings settings() {
        return new EconomySettings();
    }

    /** Справочник имён: Alice и Bob известны, Carol нет. */
    public static PlayerLookup lookup() {
        Map<UUID, String> names = new LinkedHashMap<>();
        names.put(ALICE, "Alice");
        names.put(BOB, "Bob");
        return lookup(names);
    }

    public static PlayerLookup lookup(Map<UUID, String> names) {
        return new PlayerLookup() {

            @Override
            public String name(UUID player) {
                return names.get(player);
            }

            @Override
            public boolean has(UUID player, String node) {
                return false;
            }

            @Override
            public Optional<String> meta(UUID player, String key) {
                return Optional.empty();
            }
        };
    }

    public static PlayerLookup lookupWithMeta(Map<UUID, Map<String, String>> meta) {
        return new PlayerLookup() {

            @Override
            public String name(UUID player) {
                return player.equals(ALICE) ? "Alice" : player.equals(BOB) ? "Bob" : null;
            }

            @Override
            public boolean has(UUID player, String node) {
                return false;
            }

            @Override
            public Optional<String> meta(UUID player, String key) {
                Map<String, String> values = meta.get(player);
                return values == null ? Optional.empty() : Optional.ofNullable(values.get(key));
            }
        };
    }

    public static TransferRequest transfer(UUID from, UUID to, long amount, String transactionId) {
        return TransferRequest.transfer(from, to, amount, CurrencyIds.DEFAULT, transactionId, from, "test");
    }

    public static TransferRequest deposit(UUID to, long amount, String transactionId) {
        return TransferRequest.deposit(to, amount, CurrencyIds.DEFAULT, transactionId, null, "test");
    }

    public static TransferRequest withdraw(UUID from, long amount, String transactionId) {
        return TransferRequest.withdraw(from, amount, CurrencyIds.DEFAULT, transactionId, from, "test");
    }

    public static TransferRequest set(UUID target, long amount, String transactionId) {
        return TransferRequest.set(target, amount, CurrencyIds.DEFAULT, transactionId, null, "test");
    }

    public static TransferRequest reset(UUID target, String transactionId) {
        return TransferRequest.reset(target, CurrencyIds.DEFAULT, transactionId, null, "test");
    }

    /** Движок с подставным провайдером: запись идёт в память, файлы не трогаются. */
    public static Ledger ledger(EconomyStore store, List<CurrencyRecord> currencies, EconomySettings config,
        PlayerLookup lookup, LongSupplier clock) {
        return ledger(store, null, currencies, config, lookup, clock, LOG);
    }

    public static Ledger ledger(EconomyStore store, JsonEconomyStore builtin, List<CurrencyRecord> currencies,
        EconomySettings config, PlayerLookup lookup, LongSupplier clock) {
        return ledger(store, builtin, currencies, config, lookup, clock, LOG);
    }

    /** То же с подставным логгером: тесты аудита читают, что движок записал. */
    public static Ledger ledger(EconomyStore store, List<CurrencyRecord> currencies, EconomySettings config,
        PlayerLookup lookup, LongSupplier clock, Logger log) {
        return ledger(store, null, currencies, config, lookup, clock, log);
    }

    public static Ledger ledger(EconomyStore store, JsonEconomyStore builtin, List<CurrencyRecord> currencies,
        EconomySettings config, PlayerLookup lookup, LongSupplier clock, Logger log) {
        Ledger ledger = new Ledger(
            store,
            builtin,
            currencies,
            config.currencyId(),
            config,
            config.ceilings(),
            GuardChain.of(Collections.emptyList(), config.guards.failOpen, log),
            new EventDispatcher(log),
            lookup,
            clock,
            log);
        ledger.start(store.load());
        return ledger;
    }

    /** Провайдер в памяти: считает обращения и умеет ломаться по флагу. */
    public static class MemoryStore implements EconomyStore {

        public final List<ChangeBatch> applied = new java.util.ArrayList<>();
        public StoreSnapshot snapshot = StoreSnapshot.empty();
        public boolean refuse;
        public boolean throwOnApply;
        public int saved;

        @Override
        public String id() {
            return "memory";
        }

        @Override
        public StoreSnapshot load() {
            return snapshot;
        }

        @Override
        public StoreResult apply(ChangeBatch batch) {
            if (throwOnApply) {
                throw new IllegalStateException("provider is broken");
            }
            if (refuse) {
                return StoreResult.failure(StoreResult.Failure.WRITE_FAILED, "disk is gone");
            }
            applied.add(batch);
            snapshot = snapshotOf(batch);
            return StoreResult.success();
        }

        @Override
        public StoreResult save(StoreSnapshot savedSnapshot) {
            saved++;
            snapshot = savedSnapshot;
            return StoreResult.success();
        }

        private StoreSnapshot snapshotOf(ChangeBatch batch) {
            Map<UUID, AccountView> accounts = new LinkedHashMap<>(snapshot.accounts());
            for (AccountView account : batch.upserts()) {
                accounts.put(account.uuid(), account);
            }
            List<TransactionRecord> records = new java.util.ArrayList<>(snapshot.transactions());
            records.addAll(batch.records());
            return StoreSnapshot.of(accounts, snapshot.checkpointSeq(), records);
        }
    }

    /** Причина для тестов, где важен только вид операции. */
    public static ChangeCause cause() {
        return ChangeCause.COMMAND;
    }
}
