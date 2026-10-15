package it.buonacaccia.service;

import it.buonacaccia.model.Enrollment;
import it.buonacaccia.model.ObligationStatus;
import it.buonacaccia.model.ObligationType;

/** Regole comuni sul pagamento (feature 005). */
public final class PaymentRules {

    private PaymentRules() {
    }

    /**
     * Il pagamento è abilitato solo se il capo unità ha permesso di partecipare (decisione di Mahima, 15/10):
     * l'autorizzazione, se l'evento la prevede, non deve essere più da dare né negata.
     */
    public static boolean authorized(Enrollment enrollment) {
        return enrollment.getObligations().stream()
                .filter(o -> o.getType() == ObligationType.UNIT_LEADER_AUTHORIZATION)
                .allMatch(o -> o.getStatus() == ObligationStatus.SATISFIED);
    }
}
