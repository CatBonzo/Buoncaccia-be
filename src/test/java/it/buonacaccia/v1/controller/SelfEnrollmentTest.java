package it.buonacaccia.v1.controller;

import static it.buonacaccia.v1.controller.EventManagementTest.assertError;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** US1: iscriversi da soli a un evento di qualsiasi regione, con posti e lista d'attesa. */
class SelfEnrollmentTest extends EnrollmentTestSupport {

    @Test
    void roverEnrollsInSeatsWithNumberDeadlineAndObligations() {
        JsonNode event = publish(openEvent());
        Instant before = Instant.now();

        JsonNode enrollment = enrolled("rover", event.get("id").asString());

        assertThat(enrollment.get("status").asString()).isEqualTo("REGISTERED");
        assertThat(enrollment.get("position").asString()).isEqualTo("IN_SEATS");
        assertThat(enrollment.get("arrivalOrder").asLong()).isEqualTo(1);
        assertThat(enrollment.get("number").asString()).isEqualTo(event.get("code").asString() + "-0001");
        assertThat(enrollment.get("participantMemberCode").asString()).isEqualTo("100008");
        assertThat(enrollment.get("presenterId").isNull()).isTrue();
        assertThat(enrollment.get("active").asBoolean()).isTrue();
        assertThat(enrollment.get("waitingPosition").isNull()).isTrue();
        assertThat(enrollment.get("eventTitle").asString()).isEqualTo(event.get("title").asString());
        // BR-04: 9 giorni di calendario.
        Instant deadline = Instant.parse(enrollment.get("deadline").asString());
        Instant registered = Instant.parse(enrollment.get("registeredAt").asString());
        assertThat(registered).isAfterOrEqualTo(before.minusSeconds(1));
        assertThat(deadline).isEqualTo(registered.atZone(ZoneId.of("Europe/Rome")).plusDays(9).toInstant());
        // Adempimenti del tipo ROSS: autorizzazione del capo clan, scheda, pagamento (quota 150).
        assertThat(obligation(enrollment, "UNIT_LEADER_AUTHORIZATION").get("status").asString()).isEqualTo("TODO");
        assertThat(obligation(enrollment, "DOCUMENT").get("status").asString()).isEqualTo("TODO");
        assertThat(obligation(enrollment, "PAYMENT").get("status").asString()).isEqualTo("TODO");
        assertThat(enrollment.get("availableActions").valueStream().map(JsonNode::asString)).contains("WITHDRAW");
    }

    @Test
    void regionOfTheEventDoesNotMatter() {
        String ligurian = publishOpen("region", "LIG");

        assertThat(enroll("rover", ligurian).status()).isEqualTo(201);
        assertThat(enroll("rover-lig", publishOpen()).status()).isEqualTo(201);
    }

    @Test
    void beyondTheSeatsEnrollmentsWaitInArrivalOrderWithoutPayment() {
        String eventId = publishOpen("seats", 1);
        enrolled("rover", eventId);

        JsonNode second = enrolled("scolta", eventId);
        JsonNode third = enrolled("rover-lig", eventId);

        assertThat(second.get("position").asString()).isEqualTo("WAITING");
        assertThat(second.get("waitingPosition").asInt()).isEqualTo(1);
        assertThat(third.get("waitingPosition").asInt()).isEqualTo(2);
        assertThat(obligation(second, "PAYMENT")).isNull();
        assertThat(third.get("arrivalOrder").asLong()).isEqualTo(3);
    }

    @Test
    void enrollingTwiceIsAConflict() {
        String eventId = publishOpen();
        enrolled("rover", eventId);

        assertError(enroll("rover", eventId), 409, 40911);
    }

    @Test
    void registrationsMustBeOpen() {
        String notYetOpen = publish(openEvent("registrationOpenDate", today().plusDays(1).toString())).get("id").asString();
        assertError(enroll("rover", notYetOpen), 409, 40912);

        JsonNode closedEarly = publish(openEvent());
        Result closed = post(EVENTS + "/" + closedEarly.get("id").asString() + "/close-registrations", tokenOf("segreteria"),
                Map.of("version", closedEarly.get("version").asLong()));
        assertThat(closed.status()).isEqualTo(200);
        assertError(enroll("rover", closedEarly.get("id").asString()), 409, 40912);

        // Il giorno di apertura le iscrizioni partono alle 09:00 italiane (BR-03).
        String opensToday = publish(openEvent("registrationOpenDate", today().toString())).get("id").asString();
        clock.setInstant(today().atTime(8, 59).atZone(ZoneId.of("Europe/Rome")).toInstant());
        assertError(enroll("rover", opensToday), 409, 40912);
        clock.setInstant(today().atTime(9, 0).atZone(ZoneId.of("Europe/Rome")).toInstant());
        assertThat(enroll("rover", opensToday).status()).isEqualTo(201);
    }

    @Test
    void draftsAndUnknownEventsAreNotFound() {
        Result draft = post(EVENTS, tokenOf("segreteria"), openEvent());

        assertError(enroll("rover", draft.body().get("payload").get("id").asString()), 404, 40400);
        assertError(enroll("rover", "nessuno"), 404, 40400);
    }

    @Test
    void tokenIsRequired() {
        assertError(post(EVENTS + "/" + publishOpen() + "/enrollments", null, null), 401, 40100);
    }

    @Test
    void nonMembersAndMembersNotInGoodStandingAreRejected() {
        String eventId = publishOpen("requirements", Map.of());

        Result external = enroll("esterno", eventId);
        assertError(external, 422, 42210);
        assertThat(fields(external.body().get("payload"))).containsExactly("census");
        Result notInGoodStanding = enroll("socio-non-in-regola", eventId);
        assertError(notInGoodStanding, 422, 42210);
        assertThat(fields(notInGoodStanding.body().get("payload"))).containsExactly("census");
    }

    @Test
    void scoutCannotEnrollAloneAndEveryReasonIsListed() {
        Result result = enroll("esploratore", publishOpen());

        assertError(result, 422, 42210);
        assertThat(result.body().get("errorDomain").asString()).isEqualTo("ENROLLMENT");
        assertThat(fields(result.body().get("payload"))).containsExactly("enrollmentMode", "age", "branch");
    }

    @Test
    void oneActiveEnrollmentPerEventType() {
        Map<String, Object> requirements = new java.util.HashMap<>(rsRequirements());
        requirements.put("oneActivePerType", true);
        enrolled("rover", publishOpen("requirements", requirements));

        Result second = enroll("rover", publishOpen("requirements", requirements));

        assertError(second, 422, 42210);
        assertThat(fields(second.body().get("payload"))).containsExactly("oneActivePerType");
    }

    @Test
    void maxPerGroupCountsActiveEnrollmentsOfTheSameGroup() {
        Map<String, Object> requirements = new java.util.HashMap<>(rsRequirements());
        requirements.put("maxPerGroup", 1);
        String eventId = publishOpen("requirements", requirements);
        enrolled("rover", eventId);

        Result sameGroup = enroll("scolta", eventId);
        assertError(sameGroup, 422, 42210);
        assertThat(fields(sameGroup.body().get("payload"))).containsExactly("maxPerGroup");
        // Un altro gruppo non è toccato.
        assertThat(enroll("rover-lig", eventId).status()).isEqualTo(201);
    }

    @Test
    void catalogShowsActiveEnrollmentsAndAvailability() {
        JsonNode event = publish(openEvent("seats", 2));
        String eventId = event.get("id").asString();
        enrolled("rover", eventId);
        enrolled("scolta", eventId);

        JsonNode full = get(EVENTS + "/" + eventId).body().get("payload");
        assertThat(full.get("enrolledCount").asInt()).isEqualTo(2);
        assertThat(full.get("availability").asString()).isEqualTo("LIMITED");

        enrolled("rover-lig", eventId);
        JsonNode overbooked = get(EVENTS + "/" + eventId).body().get("payload");
        assertThat(overbooked.get("enrolledCount").asInt()).isEqualTo(3);
        assertThat(overbooked.get("availability").asString()).isEqualTo("HEAVY_OVERBOOKING");

        String q = event.get("title").asString().substring("Route di prova ".length());
        JsonNode page = get(EVENTS + "?q=" + q + "&availability=HEAVY_OVERBOOKING").body().get("payload");
        assertThat(page.get("totalElements").asInt()).isEqualTo(1);
        assertThat(page.get("content").get(0).get("enrolledCount").asInt()).isEqualTo(3);
        assertThat(get(EVENTS + "?q=" + q + "&availability=OPEN").body().get("payload").get("totalElements").asInt()).isZero();
    }
}
