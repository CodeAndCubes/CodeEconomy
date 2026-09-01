package com.mrleonardos.codeeconomy.api.guard;

import java.util.Objects;
import java.util.Optional;

/**
 * Ответ гварда: пускает или ветит.
 *
 * <p>
 * Первый отказ останавливает цепочку, операция отклоняется кодом {@code GUARD_VETO}, а причина гварда
 * попадает в лог и аудит. Отказ это значение, а не исключение: правило не пускать деньги это штатный
 * ход конвейера.
 */
public final class GuardResult {

    private static final GuardResult ALLOW = new GuardResult(true, null);

    private final boolean allowed;
    private final String reason;

    private GuardResult(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }

    /** Операцию пускаем. */
    public static GuardResult allow() {
        return ALLOW;
    }

    /**
     * Операцию ветим.
     *
     * @param reason почему, попадает в лог и аудит
     */
    public static GuardResult deny(String reason) {
        Objects.requireNonNull(reason, "reason");
        return new GuardResult(false, reason);
    }

    /** Операцию ветим без пояснения. */
    public static GuardResult deny() {
        return new GuardResult(false, null);
    }

    /** Правда ли операцию пускаем. */
    public boolean allowed() {
        return allowed;
    }

    /** Причина отказа или пустой ответ. */
    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GuardResult)) {
            return false;
        }
        GuardResult that = (GuardResult) other;
        return allowed == that.allowed && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(allowed) * 31 + Objects.hashCode(reason);
    }

    @Override
    public String toString() {
        return allowed ? "allow" : "deny" + (reason == null ? "" : ": " + reason);
    }
}
