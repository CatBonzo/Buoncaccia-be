package it.buonacaccia.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import it.buonacaccia.model.AuthorizationLink;
import it.buonacaccia.model.Enrollment;
import it.buonacaccia.model.EnrollmentObligation;
import it.buonacaccia.model.EnrollmentStatus;
import it.buonacaccia.model.ObligationStatus;
import it.buonacaccia.model.ObligationType;
import org.junit.jupiter.api.Test;

/** Validità del link di autorizzazione (FR-017 della 004, research R8), senza database. */
class AuthorizationLinkValidityTest {

    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

    @Test
    void validoSeNonUsatoIscrizioneAttivaAutorizzazioneDaDareEPrimaDellaScadenza() {
        assertThat(AuthorizationLinkService.isValid(link(null), enrollment(true, ObligationStatus.TODO, NOW.plusSeconds(60)), NOW))
                .isTrue();
    }

    @Test
    void nonValidoSeGiaUsato() {
        assertThat(AuthorizationLinkService.isValid(link(NOW.minusSeconds(5)), enrollment(true, ObligationStatus.TODO, NOW.plusSeconds(60)), NOW))
                .isFalse();
    }

    @Test
    void nonValidoSeLIscrizioneNonEAttiva() {
        assertThat(AuthorizationLinkService.isValid(link(null), enrollment(false, ObligationStatus.TODO, NOW.plusSeconds(60)), NOW))
                .isFalse();
    }

    @Test
    void nonValidoSeLAutorizzazioneEGiaStataData() {
        assertThat(AuthorizationLinkService.isValid(link(null), enrollment(true, ObligationStatus.SATISFIED, NOW.plusSeconds(60)), NOW))
                .isFalse();
    }

    @Test
    void nonValidoDopoLaScadenzaAncheSeIlJobNonHaAncoraChiusoLIscrizione() {
        assertThat(AuthorizationLinkService.isValid(link(null), enrollment(true, ObligationStatus.TODO, NOW), NOW))
                .isFalse();
    }

    @Test
    void nonValidoSenzaIscrizione() {
        assertThat(AuthorizationLinkService.isValid(link(null), null, NOW)).isFalse();
    }

    private static AuthorizationLink link(Instant usedAt) {
        return AuthorizationLink.builder().obligationType(ObligationType.UNIT_LEADER_AUTHORIZATION).usedAt(usedAt).build();
    }

    private static Enrollment enrollment(boolean active, ObligationStatus status, Instant deadline) {
        List<EnrollmentObligation> obligations = new ArrayList<>();
        obligations.add(EnrollmentObligation.builder().type(ObligationType.UNIT_LEADER_AUTHORIZATION).status(status).build());
        return Enrollment.builder()
                .active(active)
                .status(active ? EnrollmentStatus.REGISTERED : EnrollmentStatus.WITHDRAWN)
                .deadline(deadline)
                .obligations(obligations)
                .build();
    }
}
