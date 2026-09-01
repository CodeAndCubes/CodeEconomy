package com.mrleonardos.codeeconomy.internal.command;

import java.util.OptionalLong;

import com.mrleonardos.codeeconomy.api.Amounts;
import com.mrleonardos.codeeconomy.api.model.CurrencyRecord;

public final class AmountArgument {

    public static final int MAX_INPUT = 25;

    private AmountArgument() {}

    public static OptionalLong parse(String raw, CurrencyRecord currency) {
        if (raw == null || raw.length() > MAX_INPUT) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Amounts.parse(raw, currency));
        } catch (IllegalArgumentException invalid) {
            return OptionalLong.empty();
        }
    }
}
