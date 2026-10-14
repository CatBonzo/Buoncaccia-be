package it.buonacaccia.v1.controller;

import static it.buonacaccia.v1.controller.EventManagementTest.assertError;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** US5: le mie iscrizioni, quelle presentate, gli iscritti per la segreteria e l'annullamento dell'evento. */
class EnrollmentListsTest extends EnrollmentTestSupport {

    private static List<String> ids(JsonNode page) {
        return page.get("content").valueStream().map(e -> e.get("id").asString()).toList();
    }

    @Test
    void myEnrollmentsNewestFirstWithEventData() {
        JsonNode first = publish(openEvent());
        String older = enrolled("rover", first.get("id").asString()).get("id").asString();
        String newer = enrolled("rover", publishOpen()).get("id").asString();

        JsonNode page = get(ENROLLMENTS + "/mine", tokenOf("rover")).body().get("payload");

        assertThat(ids(page)).containsExactly(newer, older);
        assertThat(page.get("content").get(1).get("eventTitle").asString()).isEqualTo(first.get("title").asString());
        assertThat(page.get("content").get(1).get("eventStartDate").asString()).isEqualTo(first.get("startDate").asString());
        assertThat(get(ENROLLMENTS + "/mine", tokenOf("scolta")).body().get("payload").get("totalElements").asInt()).isZero();
        assertError(get(ENROLLMENTS + "/mine?size=0", tokenOf("rover")), 422, 42200);
    }

    @Test
    void presentedEnrollmentsOfTheLeader() {
        String scout = enrollFor("socio", "100006", publish(scoutEvent()).get("id").asString())
                .body().get("payload").get("id").asString();

        assertThat(ids(get(ENROLLMENTS + "/presented", tokenOf("socio")).body().get("payload"))).containsExactly(scout);
        assertThat(ids(get(ENROLLMENTS + "/mine", tokenOf("esploratore")).body().get("payload"))).containsExactly(scout);
    }

    @Test
    void secretariatListsTheEnrollmentsByArrivalWithFilters() {
        String eventId = publishOpen("seats", 1);
        String rover = enrolled("rover", eventId).get("id").asString();
        String scolta = enrolled("scolta", eventId).get("id").asString();
        String roverLig = enrolled("rover-lig", eventId).get("id").asString();
        String list = EVENTS + "/" + eventId + "/enrollments";

        assertThat(ids(get(list, tokenOf("segreteria")).body().get("payload"))).containsExactly(rover, scolta, roverLig);
        assertThat(ids(get(list + "?position=WAITING", tokenOf("segreteria")).body().get("payload")))
                .containsExactly(scolta, roverLig);
        assertThat(ids(get(list + "?position=IN_SEATS&status=REGISTERED", tokenOf("amministratore")).body().get("payload")))
                .containsExactly(rover);
        assertError(get(list, tokenOf("segreteria-lig")), 403, 40300);
        assertError(get(list, tokenOf("rover")), 403, 40300);
        assertError(get(EVENTS + "/nessuno/enrollments", tokenOf("segreteria")), 404, 40400);
    }

    @Test
    void cancellingTheEventCancelsEveryActiveEnrollment() {
        JsonNode event = publish(openEvent("seats", 1));
        String eventId = event.get("id").asString();
        String rover = enrolled("rover", eventId).get("id").asString();
        String scolta = enrolled("scolta", eventId).get("id").asString();

        Result cancelled = post(EVENTS + "/" + eventId + "/cancel", tokenOf("segreteria"),
                Map.of("reason", "Maltempo", "version", event.get("version").asLong()));
        assertThat(cancelled.status()).as("corpo: %s", cancelled.body()).isEqualTo(200);

        JsonNode inSeats = enrollment("rover", rover);
        assertThat(inSeats.get("status").asString()).isEqualTo("CANCELLED");
        assertThat(inSeats.get("closingReason").asString()).isEqualTo("EVENT_CANCELLED");
        assertThat(inSeats.get("closingNote").asString()).isEqualTo("Maltempo");
        assertThat(inSeats.get("refundDue").asBoolean()).isTrue();
        assertThat(enrollment("scolta", scolta).get("refundDue").asBoolean()).isFalse();
        assertThat(get(EVENTS + "/" + eventId).body().get("payload").get("enrolledCount").asInt()).isZero();
    }
}
