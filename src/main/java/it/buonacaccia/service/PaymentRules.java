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
     * l'autorizzazione, se l'evento la prevede, non deve essere più da dare né negata. Basta anche quella del capo
     * gruppo, che autorizza tutto il suo gruppo (24/10).
     */
    public static boolean authorized(Enrollment enrollment) {
        boolean groupLeaderAuthorized = enrollment.getObligations().stream()
                .anyMatch(o -> o.getType() == ObligationType.GROUP_LEADER_AUTHORIZATION && o.getStatus() == ObligationStatus.SATISFIED);
        return groupLeaderAuthorized || enrollment.getObligations().stream()
                .filter(o -> o.getType() == ObligationType.UNIT_LEADER_AUTHORIZATION)
                .allMatch(o -> o.getStatus() == ObligationStatus.SATISFIED);
    }
}
