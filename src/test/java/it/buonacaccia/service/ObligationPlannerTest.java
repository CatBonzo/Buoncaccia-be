package it.buonacaccia.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import it.buonacaccia.model.Enrollment;
import it.buonacaccia.model.EnrollmentObligation;
import it.buonacaccia.model.EnrollmentStatus;
import it.buonacaccia.model.Event;
import it.buonacaccia.model.Obligation;
import it.buonacaccia.model.ObligationStatus;
import it.buonacaccia.model.ObligationType;
import it.buonacaccia.model.SeatPosition;
import org.junit.jupiter.api.Test;

/** Adempimenti iniziali e alla promozione (FR-007, FR-011, FR-017, research R7). */
class ObligationPlannerTest {

    private static final Instant NOW = Instant.parse("2027-03-10T10:00:00Z");

    @Test
    void leaderValidationIsSatisfiedWhenThePresenterEnrolls() {
        Event event = event(150, ObligationType.LEADER_VALIDATION);

        assertThat(statusOf(ObligationPlanner.initial(event, SeatPosition.IN_SEATS, "capo", NOW), ObligationType.LEADER_VALIDATION))
                .isEqualTo(ObligationStatus.SATISFIED);
        assertThat(statusOf(ObligationPlanner.initial(event, SeatPosition.IN_SEATS, null, NOW), ObligationType.LEADER_VALIDATION))
                .isEqualTo(ObligationStatus.NOT_REQUIRED);
    }

    @Test
    void authorizationsAndDocumentsStartToDo() {
        List<EnrollmentObligation> obligations = ObligationPlanner.initial(event(0, ObligationType.UNIT_LEADER_AUTHORIZATION,
                ObligationType.GROUP_LEADER_AUTHORIZATION, ObligationType.DOCUMENT, ObligationType.ELECTRONIC_FORM),
                SeatPosition.IN_SEATS, null, NOW);

        assertThat(obligations).extracting(EnrollmentObligation::getStatus).containsOnly(ObligationStatus.TODO);
        assertThat(obligations).hasSize(4);
    }

    @Test
    void unAdempimentoPerOgniDocumentoConIVincoliDellEvento() {
        Event event = event(0);
        event.setObligations(List.of(
                Obligation.builder().type(ObligationType.DOCUMENT).code("FOTO").allowedMimeTypes(List.of("image/jpeg"))
                        .maxSizeBytes(1_048_576L).build(),
                Obligation.builder().type(ObligationType.DOCUMENT).code("LIBERATORIA")
                        .delivery(it.buonacaccia.model.DocumentDelivery.PAPER).build(),
                Obligation.builder().type(ObligationType.DOCUMENT).description("senza codice").build()));

        List<EnrollmentObligation> obligations = ObligationPlanner.initial(event, SeatPosition.IN_SEATS, null, NOW);

        assertThat(obligations).extracting(EnrollmentObligation::getCode).containsExactly("FOTO", "LIBERATORIA", "DOC");
        assertThat(obligations.get(0).getAllowedMimeTypes()).containsExactly("image/jpeg");
        assertThat(obligations.get(0).getMaxSizeBytes()).isEqualTo(1_048_576L);
        assertThat(obligations.get(1).getDelivery()).isEqualTo(it.buonacaccia.model.DocumentDelivery.PAPER);
        assertThat(obligations.get(2).getMaxSizeBytes()).isEqualTo(5L * 1024 * 1024);
        assertThat(obligations.get(2).getAllowedMimeTypes()).hasSize(3);
    }

    @Test
    void ilDocumentoInVerificaNonCompletaLIscrizione() {
        EnrollmentObligation review = EnrollmentObligation.builder().type(ObligationType.DOCUMENT)
                .status(ObligationStatus.IN_REVIEW).build();
        EnrollmentObligation done = EnrollmentObligation.builder().type(ObligationType.DOCUMENT)
                .status(ObligationStatus.SATISFIED).build();

        assertThat(ObligationPlanner.statusFor(List.of(review, done))).isEqualTo(EnrollmentStatus.REGISTERED);
        assertThat(ObligationPlanner.statusFor(List.of(done))).isEqualTo(EnrollmentStatus.COMPLETED);
    }

    @Test
    void paymentDependsOnFeeAndPosition() {
        assertThat(types(ObligationPlanner.initial(event(150, ObligationType.PAYMENT), SeatPosition.IN_SEATS, null, NOW)))
                .containsExactly(ObligationType.PAYMENT);
        // Quota 0: niente pagamento.
        assertThat(ObligationPlanner.initial(event(0, ObligationType.PAYMENT), SeatPosition.IN_SEATS, null, NOW)).isEmpty();
        // Quota > 0 anche se l'evento non lo elenca (BR-05).
        assertThat(types(ObligationPlanner.initial(event(80), SeatPosition.IN_SEATS, null, NOW)))
                .containsExactly(ObligationType.PAYMENT);
        // In attesa non si paga (FR-017).
        assertThat(ObligationPlanner.initial(event(150, ObligationType.PAYMENT), SeatPosition.WAITING, null, NOW)).isEmpty();
    }

    @Test
    void statusIsCompletedOnlyWithNothingToDo() {
        assertThat(ObligationPlanner.statusFor(List.of())).isEqualTo(EnrollmentStatus.COMPLETED);
        assertThat(ObligationPlanner.statusFor(ObligationPlanner.initial(event(0, ObligationType.LEADER_VALIDATION),
                SeatPosition.IN_SEATS, "capo", NOW))).isEqualTo(EnrollmentStatus.COMPLETED);
        assertThat(ObligationPlanner.statusFor(ObligationPlanner.initial(event(150), SeatPosition.IN_SEATS, null, NOW)))
                .isEqualTo(EnrollmentStatus.REGISTERED);
    }

    @Test
    void promotionAddsPaymentAndRestartsTheDeadline() {
        Enrollment enrollment = Enrollment.builder().status(EnrollmentStatus.COMPLETED).position(SeatPosition.WAITING)
                .obligations(new java.util.ArrayList<>()).deadline(NOW.minus(1, ChronoUnit.DAYS)).build();

        ObligationPlanner.onPromotion(enrollment, event(150, ObligationType.PAYMENT), NOW);

        assertThat(enrollment.getPosition()).isEqualTo(SeatPosition.IN_SEATS);
        assertThat(enrollment.getPromotedAt()).isEqualTo(NOW);
        assertThat(types(enrollment.getObligations())).containsExactly(ObligationType.PAYMENT);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.REGISTERED);
        assertThat(enrollment.getDeadline()).isEqualTo(Instant.parse("2027-03-19T10:00:00Z"));
    }

    @Test
    void promotionWithoutFeeKeepsTheEnrollmentAsItIs() {
        Instant deadline = NOW.plus(3, ChronoUnit.DAYS);
        Enrollment enrollment = Enrollment.builder().status(EnrollmentStatus.COMPLETED).position(SeatPosition.WAITING)
                .obligations(new java.util.ArrayList<>()).deadline(deadline).build();

        ObligationPlanner.onPromotion(enrollment, event(0), NOW);

        assertThat(enrollment.getPosition()).isEqualTo(SeatPosition.IN_SEATS);
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
        assertThat(enrollment.getDeadline()).isEqualTo(deadline);
    }

    @Test
    void deadlineIsNineCalendarDaysInRome() {
        // Il 28/03/2027 l'ora legale accorcia il giorno: la scadenza resta alla stessa ora italiana.
        Instant registered = Instant.parse("2027-03-25T09:00:00Z"); // 10:00 in Italia
        assertThat(ObligationPlanner.deadlineFrom(registered)).isEqualTo(Instant.parse("2027-04-03T08:00:00Z"));
    }

    private static Event event(int fee, ObligationType... types) {
        List<Obligation> obligations = java.util.Arrays.stream(types)
                .map(type -> Obligation.builder().type(type).description(type.name()).build()).toList();
        return Event.builder().id("e").fee(BigDecimal.valueOf(fee)).obligations(obligations).build();
    }

    private static ObligationStatus statusOf(List<EnrollmentObligation> obligations, ObligationType type) {
        return obligations.stream().filter(o -> o.getType() == type).findFirst().orElseThrow().getStatus();
    }

    private static List<ObligationType> types(List<EnrollmentObligation> obligations) {
        return obligations.stream().map(EnrollmentObligation::getType).toList();
    }
}
