package com.mrleonardos.codeeconomy.api.event;

import java.util.Objects;
import java.util.Optional;

/**
 * Вход или выход из режима деградации.
 *
 * <p>
 * В деградации мутации отклоняются, чтения работают по последнему записанному состоянию. Событие
 * приходит и на вход, и на выход, поэтому слушатель умеет держать свой кеш в согласии с носителем.
 */
public final class DegradedEvent {

    private final boolean degraded;
    private final String provider;
    private final String reason;

    private DegradedEvent(boolean degraded, String provider, String reason) {
        this.degraded = degraded;
        this.provider = provider;
        this.reason = reason;
    }

    /**
     * Собрать событие.
     *
     * @param provider имя провайдера хранилища
     * @param reason   почему режим меняется или пустой ответ
     */
    public static DegradedEvent of(boolean degraded, String provider, String reason) {
        Objects.requireNonNull(provider, "provider");
        return new DegradedEvent(degraded, provider, reason);
    }

    /** Правда ли мод вошёл в деградацию, иначе вышел из неё. */
    public boolean degraded() {
        return degraded;
    }

    /** Имя провайдера хранилища. */
    public String provider() {
        return provider;
    }

    /** Причина смены режима или пустой ответ. */
    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DegradedEvent)) {
            return false;
        }
        DegradedEvent that = (DegradedEvent) other;
        return degraded == that.degraded && provider.equals(that.provider) && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return (Boolean.hashCode(degraded) * 31 + provider.hashCode()) * 31 + Objects.hashCode(reason);
    }

    @Override
    public String toString() {
        return (degraded ? "degraded" : "restored") + " " + provider;
    }
}
