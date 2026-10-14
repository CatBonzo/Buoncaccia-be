package it.buonacaccia.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import it.buonacaccia.config.ClockConfig;
import it.buonacaccia.model.SeatPosition;

/**
 * Diritto al rimborso (BR-10, decisione di Mahima del 14/10/2026 su Q12): spetta se il ritiro avviene
 * almeno 7 giorni di calendario prima della partenza, contati in ora italiana e con la data di
 * partenza esclusa, a chi era nei posti con una quota da pagare. Chi è in attesa non ha pagato.
 */
public final class RefundRule {

    public static final int MIN_DAYS_BEFORE_START = 7;

    private RefundRule() {
    }

    public static boolean refundDue(BigDecimal fee, LocalDate startDate, SeatPosition position, Instant when) {
        if (fee == null || fee.signum() <= 0 || position != SeatPosition.IN_SEATS || startDate == null) {
            return false;
        }
        LocalDate day = when.atZone(ClockConfig.ROME).toLocalDate();
        return ChronoUnit.DAYS.between(day, startDate) >= MIN_DAYS_BEFORE_START;
    }
}
