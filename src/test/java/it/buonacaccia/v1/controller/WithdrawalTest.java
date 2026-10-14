package it.buonacaccia.v1.controller;

import static it.buonacaccia.v1.controller.EventManagementTest.assertError;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;

import it.buonacaccia.service.EnrollmentJobs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** US4: ritiro con diritto al rimborso (BR-10) e promozioni dalla lista d'attesa (BR-08, FR-014). */
class WithdrawalTest extends EnrollmentTestSupport {

    @Autowired
    private EnrollmentJobs jobs;

    private Result withdraw(String username, String id) {
        return post(ENROLLMENTS + "/" + id + "/withdraw", tokenOf(username), null);
    }

    @Test
    void withdrawalWellBeforeStartIsRefundedAndPromotesTheFirstWaiting() {
        String eventId = publishOpen("seats", 1);
        String rover = enrolled("rover", eventId).get("id").asString();
        String scolta = enrolled("scolta", eventId).get("id").asString();
        String roverLig = enrolled("rover-lig", eventId).get("id").asString();

        Result result = withdraw("rover", rover);

        assertThat(result.status()).as("corpo: %s", result.body()).isEqualTo(200);
        JsonNode withdrawn = result.body().get("payload");
        assertThat(withdrawn.get("status").asString()).isEqualTo("WITHDRAWN");
        assertThat(withdrawn.get("refundDue").asBoolean()).isTrue();
        assertThat(withdrawn.get("active").asBoolean()).isFalse();
        JsonNode promoted = enrollment("scolta", scolta);
        assertThat(promoted.get("position").asString()).isEqualTo("IN_SEATS");
        assertThat(promoted.get("promotedAt").isNull()).isFalse();
        assertThat(obligation(promoted, "PAYMENT").get("status").asString()).isEqualTo("TODO");
        assertThat(enrollment("rover-lig", roverLig).get("waitingPosition").asInt()).isEqualTo(1);

        assertError(withdraw("rover", rover), 409, 40910);
    }

    @Test
    void onlyParticipantOrPresenterCanWithdraw() {
        String eventId = publishOpen();
        String rover = enrolled("rover", eventId).get("id").asString();
        assertError(withdraw("rover-lig", rover), 404, 40400);
        assertError(withdraw("segreteria", rover), 403, 40300);

        String scout = enrollFor("socio", "100006", publish(scoutEvent()).get("id").asString())
                .body().get("payload").get("id").asString();
        assertThat(withdraw("socio", scout).status()).isEqualTo(200);
    }

    @Test
    void lateWithdrawalHasNoRefundAndAfterStartIsNotAllowed() {
        JsonNode event = publish(openEvent());
        LocalDate start = LocalDate.parse(event.get("startDate").asString());
        String late = enrolled("rover", event.get("id").asString()).get("id").asString();
        String tooLate = enrolled("scolta", event.get("id").asString()).get("id").asString();

        clock.setInstant(romeTime(start.minusDays(3), 12, 0));
        JsonNode withdrawn = withdraw("rover", late).body().get("payload");
        assertThat(withdrawn.get("refundDue").asBoolean()).isFalse();

        clock.setInstant(romeTime(start, 0, 0));
        assertError(withdraw("scolta", tooLate), 409, 40910);
    }

    @Test
    void waitingWithdrawalHasNoRefundAndPromotesNobody() {
        String eventId = publishOpen("seats", 1);
        String rover = enrolled("rover", eventId).get("id").asString();
        String scolta = enrolled("scolta", eventId).get("id").asString();
        String roverLig = enrolled("rover-lig", eventId).get("id").asString();

        JsonNode withdrawn = withdraw("scolta", scolta).body().get("payload");

        assertThat(withdrawn.get("refundDue").asBoolean()).isFalse();
        assertThat(enrollment("rover", rover).get("position").asString()).isEqualTo("IN_SEATS");
        assertThat(enrollment("rover-lig", roverLig).get("waitingPosition").asInt()).isEqualTo(1);
        assertThat(get(EVENTS + "/" + eventId).body().get("payload").get("enrolledCount").asInt()).isEqualTo(2);
    }

    @Test
    void moreSeatsPromoteTheWaitingAndFewerThanAssignedAreRefused() {
        Map<String, Object> body = openEvent("seats", 1);
        JsonNode event = publish(body);
        String eventId = event.get("id").asString();
        enrolled("rover", eventId);
        String scolta = enrolled("scolta", eventId).get("id").asString();
        String roverLig = enrolled("rover-lig", eventId).get("id").asString();

        Result more = updateEvent(event, body, "seats", 3);

        assertThat(more.status()).as("corpo: %s", more.body()).isEqualTo(200);
        assertThat(enrollment("scolta", scolta).get("position").asString()).isEqualTo("IN_SEATS");
        assertThat(enrollment("rover-lig", roverLig).get("position").asString()).isEqualTo("IN_SEATS");

        assertError(updateEvent(more.body().get("payload"), body, "seats", 2), 409, 40905);
        assertThat(get(EVENTS + "/" + eventId).body().get("payload").get("seats").asInt()).isEqualTo(3);
    }

    @Test
    void stillWaitingAtStartIsCancelledForExhaustedSeats() {
        JsonNode event = publish(openEvent("seats", 1));
        LocalDate start = LocalDate.parse(event.get("startDate").asString());
        String rover = enrolled("rover", event.get("id").asString()).get("id").asString();
        String scolta = enrolled("scolta", event.get("id").asString()).get("id").asString();

        clock.setInstant(romeTime(start, 0, 5));
        assertThat(jobs.cancelWaitingAtStart()).isEqualTo(1);

        JsonNode cancelled = enrollment("scolta", scolta);
        assertThat(cancelled.get("status").asString()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("closingReason").asString()).isEqualTo("SEATS_EXHAUSTED");
        assertThat(enrollment("rover", rover).get("status").asString()).isEqualTo("REGISTERED");
    }
}
