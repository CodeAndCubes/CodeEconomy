package com.mrleonardos.codeeconomy.internal.adapter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Экономика ForgeEssentials через отражение.
 *
 * <p>
 * Сигнатуры сняты с ветки {@code 1.7.10/develop}, каталог {@code com.forgeessentials.api}:
 * {@code APIRegistry.economy} типа {@code Economy} отдаёт {@code Wallet} со счётом в {@code long}, а
 * игрок собирается фабрикой {@code UserIdent.get}. Компилироваться против этих классов нельзя,
 * {@code IPermissionsHelper} тянет за собой интерфейс препролоадера FE, которого в чистом Forge нет.
 *
 * <p>
 * Методы ищутся на объявляющих интерфейсах, а не на классе полученного объекта: реализация у FE
 * непубличная, и {@code invoke} на её методе отказал бы по доступу. Поле {@code economy} бывает
 * {@code null} до подъёма чужого модуля, поэтому оно читается перед каждым обращением.
 */
final class ForgeEssentialsApi {

    private static final String REGISTRY = "com.forgeessentials.api.APIRegistry";
    private static final String USER_IDENT = "com.forgeessentials.api.UserIdent";
    private static final String ECONOMY = "com.forgeessentials.api.economy.Economy";
    private static final String WALLET = "com.forgeessentials.api.economy.Wallet";

    private final Field economyField;
    private final Method identOf;
    private final Method identOfNamed;
    private final Method getWallet;
    private final Method currencyName;
    private final Method walletGet;
    private final Method walletSet;
    private final Method walletAdd;
    private final Method walletCovers;
    private final Method walletWithdraw;

    private ForgeEssentialsApi(Field economyField, Class<?> identType, Class<?> economyType, Class<?> walletType) {
        this.economyField = economyField;
        this.identOf = Reflected.method(identType, "get", UUID.class);
        this.identOfNamed = Reflected.method(identType, "get", UUID.class, String.class);
        this.getWallet = Reflected.method(economyType, "getWallet", identType);
        this.currencyName = Reflected.method(economyType, "currency", long.class);
        this.walletGet = Reflected.method(walletType, "get");
        this.walletSet = Reflected.method(walletType, "set", long.class);
        this.walletAdd = Reflected.method(walletType, "add", long.class);
        this.walletCovers = Reflected.method(walletType, "covers", long.class);
        this.walletWithdraw = Reflected.method(walletType, "withdraw", long.class);
    }

    /** Мост или {@code null}, если ForgeEssentials на этом сервере нет. */
    static ForgeEssentialsApi create() {
        Field economyField = Reflected.field(Reflected.type(REGISTRY), "economy");
        Class<?> identType = Reflected.type(USER_IDENT);
        Class<?> economyType = Reflected.type(ECONOMY);
        Class<?> walletType = Reflected.type(WALLET);
        if (economyField == null || identType == null || economyType == null || walletType == null) {
            return null;
        }
        ForgeEssentialsApi api = new ForgeEssentialsApi(economyField, identType, economyType, walletType);
        return api.complete() ? api : null;
    }

    /** Правда ли чужая экономика уже поднялась и отвечает. */
    boolean ready() {
        return Reflected.value(economyField) != null;
    }

    /**
     * Кошелёк игрока или {@code null}.
     *
     * @param name последний известный ник: FE узнаёт офлайн-игрока по паре с ним
     */
    Object wallet(UUID player, String name) {
        Object economy = Reflected.value(economyField);
        if (economy == null) {
            return null;
        }
        Object ident = name == null || name.isEmpty() ? Reflected.call(identOf, null, player)
            : Reflected.call(identOfNamed, null, player, name);
        return ident == null ? null : Reflected.call(getWallet, economy, ident);
    }

    long balance(Object wallet) {
        return Reflected.number(Reflected.call(walletGet, wallet), 0L);
    }

    boolean covers(Object wallet, long amount) {
        return Reflected.flag(Reflected.call(walletCovers, wallet, Long.valueOf(amount)), false);
    }

    boolean withdraw(Object wallet, long amount) {
        return Reflected.flag(Reflected.call(walletWithdraw, wallet, Long.valueOf(amount)), false);
    }

    void add(Object wallet, long amount) {
        Reflected.call(walletAdd, wallet, Long.valueOf(amount));
    }

    void set(Object wallet, long amount) {
        Reflected.call(walletSet, wallet, Long.valueOf(amount));
    }

    /** Как чужой мод называет свою единственную валюту. */
    String currencyName(String fallback) {
        Object economy = Reflected.value(economyField);
        if (economy == null) {
            return fallback;
        }
        return Reflected.text(Reflected.call(currencyName, economy, Long.valueOf(1L)), fallback);
    }

    private boolean complete() {
        return identOf != null && identOfNamed != null
            && getWallet != null
            && walletGet != null
            && walletSet != null
            && walletAdd != null
            && walletCovers != null
            && walletWithdraw != null;
    }
}
