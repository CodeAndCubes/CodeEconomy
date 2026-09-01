package com.mrleonardos.codeeconomy.internal;

/** Ноды прав и ключи меты экономики: один источник, движок и команды импортируют отсюда. */
public final class EconomyNodes {

    /** Обход пола баланса: уход до {@code negativeFloor} разрешён. */
    public static final String BYPASS_MIN_BALANCE = "codeeconomy.bypass.minbalance";

    /** Стартовый баланс группы в мажорных единицах вместо {@code startBalance} валюты. */
    public static final String META_STARTING = "codeeconomy.starting";

    /** Личный потолок одного перевода вместо {@code limits.maxTransfer}. */
    public static final String META_PAY_LIMIT = "codeeconomy.paylimit";

    private EconomyNodes() {}
}
