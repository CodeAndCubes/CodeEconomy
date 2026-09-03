package com.mrleonardos.codeeconomy.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.command.CommandInputException;
import com.mrleonardos.codecore.api.command.CommandSender;
import com.mrleonardos.codeeconomy.api.CurrencyIds;
import com.mrleonardos.codeeconomy.api.EconomyService;
import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.BalanceEntry;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;
import com.mrleonardos.codeeconomy.api.model.TransferRequest;
import com.mrleonardos.codeeconomy.api.model.TransferResult;
import com.mrleonardos.codeeconomy.internal.command.EconomyMessages;

class PlatformArgumentsTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Test
    void playerResolvesFromStoredAccountsWhenNobodyIsOnline() {
        Map<UUID, AccountView> accounts = new LinkedHashMap<>();
        accounts.put(STEVE, AccountView.of(STEVE, "Steve", Collections.<String, Long>emptyMap(), false, 1L));
        NameResolver names = new NameResolver(() -> accounts);
        PlatformArguments arguments = new PlatformArguments(names, economy(Collections.<CurrencyRecord>emptyList()));

        assertEquals(
            STEVE,
            arguments.player()
                .parse("Steve"));
        assertEquals(
            STEVE,
            arguments.player()
                .parse("STEVE"),
            "ник разбирается без учёта регистра");

        CommandInputException unknown = assertThrows(
            CommandInputException.class,
            () -> arguments.player()
                .parse("Alex"));
        assertEquals(EconomyMessages.FAILURE_UNKNOWN_PLAYER, unknown.translationKey());
        assertEquals("Alex", unknown.arguments()[0], "в сообщение подставляется введённый ник");
    }

    /**
     * Сторож на молчаливую перегрузку.
     *
     * <p>
     * {@code ArgumentType.suggestions} это метод по умолчанию, и подпись, разошедшаяся с той, что зовёт
     * мост команд, становится перегрузкой, а не переопределением: сборка остаётся зелёной, а подсказки
     * возвращают пустой список из тела по умолчанию. Проверить наличие {@code @Override} сборкой нельзя,
     * аннотация не доживает до класса, поэтому проверяется сама поломка.
     */
    @Test
    void theSuggestionsOfThePlayerArgumentActuallyReachTheResolver() {
        Map<UUID, AccountView> accounts = new LinkedHashMap<>();
        accounts.put(STEVE, AccountView.of(STEVE, "Steve", Collections.<String, Long>emptyMap(), false, 1L));
        PlatformArguments arguments = new PlatformArguments(
            new NameResolver(() -> accounts),
            economy(Collections.<CurrencyRecord>emptyList()));

        List<String> suggestions = arguments.player()
            .suggestions((CommandSender) null, "St");

        assertEquals(
            Collections.singletonList("Steve"),
            suggestions,
            "подсказки игрока идут в NameResolver, а не в пустое тело по умолчанию");
    }

    @Test
    void nameResolverAnswersNamesAndSuggestionsFromAccounts() {
        Map<UUID, AccountView> accounts = new LinkedHashMap<>();
        accounts.put(STEVE, AccountView.of(STEVE, "Steve", Collections.<String, Long>emptyMap(), false, 1L));
        NameResolver names = new NameResolver(() -> accounts);

        assertEquals(
            "Steve",
            names.name(STEVE)
                .orElse(""));
        assertEquals(
            STEVE,
            names.id("steve")
                .orElse(null));
        assertEquals(Collections.singletonList("Steve"), names.suggest("St", 10));
        assertTrue(
            names.suggest("Alex", 10)
                .isEmpty());
        assertTrue(
            names.suggest("St", 0)
                .isEmpty(),
            "нулевой лимит ничего не подсказывает");
    }

    @Test
    void currencyNormalizesInputAndSuggestsVisibleOnes() {
        CurrencyRecord coin = CurrencyRecord.defaultCoin();
        CurrencyRecord hidden = CurrencyRecord.builder("gem")
            .displayName("Gems")
            .visible(false)
            .startBalance(1L)
            .maxBalance(10L)
            .build();
        PlatformArguments arguments = new PlatformArguments(
            new NameResolver(Collections::emptyMap),
            economy(Arrays.asList(coin, hidden)));

        assertEquals(
            CurrencyIds.normalize("COIN"),
            arguments.currency()
                .parse("COIN"));

        assertTrue(
            arguments.currency()
                .suggestions((CommandSender) null, "g")
                .isEmpty(),
            "скрытая валюта в подсказки не попадает");
        assertEquals(
            Collections.singletonList("coin"),
            arguments.currency()
                .suggestions((CommandSender) null, "co"),
            "видимая валюта подсказывается");
    }

    private static EconomyService economy(List<CurrencyRecord> currencies) {
        return new StubEconomy(currencies);
    }

    private static final class StubEconomy implements EconomyService {

        private final List<CurrencyRecord> currencies;

        private StubEconomy(List<CurrencyRecord> currencies) {
            this.currencies = currencies;
        }

        @Override
        public List<CurrencyRecord> currencies() {
            return currencies;
        }

        @Override
        public Optional<CurrencyRecord> currency(String currencyId) {
            for (CurrencyRecord currency : currencies) {
                if (currency.id()
                    .equals(currencyId)) {
                    return Optional.of(currency);
                }
            }
            return Optional.empty();
        }

        @Override
        public String defaultCurrencyId() {
            return CurrencyIds.DEFAULT;
        }

        @Override
        public long balance(UUID player, String currencyId) {
            return 0L;
        }

        @Override
        public boolean has(UUID player, long amount, String currencyId) {
            return false;
        }

        @Override
        public Optional<AccountView> account(UUID player) {
            return Optional.empty();
        }

        @Override
        public TransferResult transfer(TransferRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransferResult deposit(TransferRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransferResult withdraw(TransferRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransferResult set(TransferRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransferResult reset(TransferRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<TransferResult> submit(TransferRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TransactionRecord> history(UUID player, int page, int pageSize) {
            return Collections.emptyList();
        }

        @Override
        public List<BalanceEntry> top(String currencyId, int page, int pageSize) {
            return Collections.emptyList();
        }
    }
}
