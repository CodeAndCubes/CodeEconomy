package com.mrleonardos.codeeconomy.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.mrleonardos.codeeconomy.api.model.AccountView;
import com.mrleonardos.codeeconomy.api.model.BalanceEntry;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;
import com.mrleonardos.codeeconomy.api.model.TransactionRecord;
import com.mrleonardos.codeeconomy.api.model.TransferRequest;
import com.mrleonardos.codeeconomy.api.model.TransferResult;

/**
 * Деньги сервера: валюты, балансы, операции, история и топ.
 *
 * <p>
 * Реализацию чужой мод берёт из реестра ядра: кто её туда поставил, решает реестр адаптеров по ключу
 * {@code [owners] economy} главного файла, и это может быть не CodeEconomy. Чтения работают из любого
 * потока без блокировок и отвечают по последнему записанному состоянию.
 *
 * <p>
 * Мутации исполняет один писатель в главном потоке. {@link #transfer} и соседние методы работают
 * только там: вызов из чужого потока даёт {@code IllegalStateException}. Сетевой обработчик и другой
 * фоновый поток зовут {@link #submit}: операция встанет в ближайший тик, а будущий результат придёт в
 * {@code CompletableFuture}. Блокирующего переноса в главный поток нет.
 *
 * <p>
 * Отказ приходит значением {@link TransferResult} с кодом из конечного перечня. Повтор операции с тем
 * же {@code transactionId} возвращает записанный исход с кодом {@code DUPLICATE}, поэтому успешным
 * исходом считается и {@code OK}, и {@code DUPLICATE}.
 */
public interface EconomyService {

    /**
     * Все валюты в порядке их объявления в файле. Владелец роли с единственной валютой отвечает списком
     * из одного элемента.
     */
    List<CurrencyRecord> currencies();

    /** Валюта по идентификатору или пустой ответ. */
    Optional<CurrencyRecord> currency(String currencyId);

    /** Идентификатор валюты из настройки {@code defaultCurrency}. */
    String defaultCurrencyId();

    /**
     * Баланс игрока в минорных единицах. Счёта ещё нет, отвечает стартовый баланс валюты, на диск при
     * этом ничего не пишется.
     */
    long balance(UUID player, String currencyId);

    /** Правда ли денег хватает: баланс не меньше суммы в минорных единицах. */
    boolean has(UUID player, long amount, String currencyId);

    /** Счёт игрока или пустой ответ, если первой мутации ещё не было. */
    Optional<AccountView> account(UUID player);

    /**
     * Перевод между счетами.
     *
     * @throws IllegalStateException если вызов пришёл не из главного потока
     */
    TransferResult transfer(TransferRequest request);

    /**
     * Выдача игроку.
     *
     * @throws IllegalStateException если вызов пришёл не из главного потока
     */
    TransferResult deposit(TransferRequest request);

    /**
     * Снятие у игрока.
     *
     * @throws IllegalStateException если вызов пришёл не из главного потока
     */
    TransferResult withdraw(TransferRequest request);

    /**
     * Установка баланса в точное значение.
     *
     * @throws IllegalStateException если вызов пришёл не из главного потока
     */
    TransferResult set(TransferRequest request);

    /**
     * Возврат баланса к стартовому.
     *
     * @throws IllegalStateException если вызов пришёл не из главного потока
     */
    TransferResult reset(TransferRequest request);

    /**
     * Та же операция, но из любого потока: она встаёт в ближайший тик главного потока и исполняется там
     * по всем правилам конвейера.
     */
    CompletableFuture<TransferResult> submit(TransferRequest request);

    /**
     * Записи журнала одного игрока, свежие раньше.
     *
     * @param page номер страницы с нуля, страница за краем даёт пустой список
     */
    List<TransactionRecord> history(UUID player, int page, int pageSize);

    /**
     * Топ по валюте, по убыванию баланса, при равенстве по нику без учёта регистра.
     *
     * @param page номер страницы с нуля, страница за краем даёт пустой список
     */
    List<BalanceEntry> top(String currencyId, int page, int pageSize);
}
