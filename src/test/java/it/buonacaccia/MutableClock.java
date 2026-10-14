package it.buonacaccia;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Orologio dei test di integrazione: segue l'ora vera finché un test non lo ferma su un istante
 * con {@link #setInstant(Instant)}; {@link #reset()} lo riporta all'ora vera (research R1).
 */
public class MutableClock extends Clock {

    private volatile Instant fixed;

    public void setInstant(Instant instant) {
        this.fixed = instant;
    }

    public void reset() {
        this.fixed = null;
    }

    @Override
    public Instant instant() {
        Instant now = fixed;
        return now != null ? now : Instant.now();
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
