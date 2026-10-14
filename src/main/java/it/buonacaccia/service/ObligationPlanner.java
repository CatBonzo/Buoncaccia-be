package it.buonacaccia.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import it.buonacaccia.config.ClockConfig;
import it.buonacaccia.model.Enrollment;
import it.buonacaccia.model.EnrollmentObligation;
import it.buonacaccia.model.EnrollmentStatus;
import it.buonacaccia.model.Event;
import it.buonacaccia.model.Obligation;
import it.buonacaccia.model.ObligationStatus;
import it.buonacaccia.model.ObligationType;
import it.buonacaccia.model.SeatPosition;

/**
 * Adempimenti di un'iscrizione e scadenza di completamento (FR-007, FR-008, FR-011, FR-017,
 * research R7). Il pagamento si chiede solo a chi è nei posti e solo se la quota è maggiore di 0,
 * anche se l'evento non lo elenca (BR-05); chi è in attesa lo riceve alla promozione.
 */
public final class ObligationPlanner {

    public static final int DEADLINE_DAYS = 9;
    private static final String DEFAULT_PAYMENT_DESCRIPTION = "Quota di partecipazione";

    private ObligationPlanner() {
    }

    /** Adempimenti alla registrazione; {@code presenterId} è il capo che ha iscritto il ragazzo, se c'è. */
    public static List<EnrollmentObligation> initial(Event event, SeatPosition position, String presenterId, Instant now) {
        List<EnrollmentObligation> result = new ArrayList<>();
        for (Obligation obligation : eventObligations(event)) {
            if (obligation.getType() == ObligationType.PAYMENT
                    || result.stream().anyMatch(o -> o.getType() == obligation.getType())) {
                continue;
            }
            EnrollmentObligation item = EnrollmentObligation.builder()
                    .type(obligation.getType()).description(obligation.getDescription()).status(ObligationStatus.TODO).build();
            if (obligation.getType() == ObligationType.LEADER_VALIDATION) {
                if (presenterId != null) {
                    item.setStatus(ObligationStatus.SATISFIED);
                    item.setSatisfiedBy(presenterId);
                    item.setSatisfiedAt(now);
                } else {
                    item.setStatus(ObligationStatus.NOT_REQUIRED);
                }
            }
            result.add(item);
        }
        if (position == SeatPosition.IN_SEATS && hasFee(event.getFee())) {
            result.add(payment(event));
        }
        return result;
    }

    /** {@code COMPLETED} se non resta nulla da fare (FR-011), altrimenti {@code REGISTERED}. */
    public static EnrollmentStatus statusFor(List<EnrollmentObligation> obligations) {
        return obligations.stream().anyMatch(o -> o.getStatus() == ObligationStatus.TODO)
                ? EnrollmentStatus.REGISTERED : EnrollmentStatus.COMPLETED;
    }

    /**
     * Promozione dalla lista d'attesa (FR-013, FR-017): l'iscrizione passa nei posti; se c'è una quota
     * riceve il pagamento, torna {@code REGISTERED} e ha 9 giorni nuovi per completare.
     */
    public static void onPromotion(Enrollment enrollment, Event event, Instant now) {
        enrollment.setPosition(SeatPosition.IN_SEATS);
        enrollment.setPromotedAt(now);
        boolean hasPayment = enrollment.getObligations().stream().anyMatch(o -> o.getType() == ObligationType.PAYMENT);
        if (hasFee(event.getFee()) && !hasPayment) {
            enrollment.getObligations().add(payment(event));
            enrollment.setStatus(EnrollmentStatus.REGISTERED);
            enrollment.setCompletedAt(null);
            enrollment.setDeadline(deadlineFrom(now));
        }
    }

    /** Scadenza: 9 giorni di calendario alla stessa ora italiana (BR-04). */
    public static Instant deadlineFrom(Instant from) {
        return from.atZone(ClockConfig.ROME).plusDays(DEADLINE_DAYS).toInstant();
    }

    private static EnrollmentObligation payment(Event event) {
        String description = eventObligations(event).stream()
                .filter(o -> o.getType() == ObligationType.PAYMENT)
                .map(Obligation::getDescription)
                .findFirst().orElse(DEFAULT_PAYMENT_DESCRIPTION);
        return EnrollmentObligation.builder()
                .type(ObligationType.PAYMENT).description(description).status(ObligationStatus.TODO).build();
    }

    private static List<Obligation> eventObligations(Event event) {
        return event.getObligations() == null ? List.of() : event.getObligations();
    }

    private static boolean hasFee(BigDecimal fee) {
        return fee != null && fee.signum() > 0;
    }
}
