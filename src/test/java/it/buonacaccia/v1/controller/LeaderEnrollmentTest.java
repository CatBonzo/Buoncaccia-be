package it.buonacaccia.v1.controller;

import static it.buonacaccia.v1.controller.EventManagementTest.assertError;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** US2: il capo unità iscrive un ragazzo della sua unità e ne diventa presentatore (BR-02). */
class LeaderEnrollmentTest extends EnrollmentTestSupport {

    @Test
    void leaderEnrollsAScoutOfTheUnitAsPresenter() {
        String eventId = publish(scoutEvent()).get("id").asString();

        Result result = enrollFor("socio", "100006", eventId);

        assertThat(result.status()).as("corpo: %s", result.body()).isEqualTo(201);
        JsonNode enrollment = result.body().get("payload");
        assertThat(enrollment.get("participantId").asString()).isEqualTo("user-006");
        assertThat(enrollment.get("participantName").asString()).isEqualTo("Elia Esploratore");
        assertThat(enrollment.get("presenterId").asString()).isEqualTo("user-003");
        assertThat(enrollment.get("presenterName").asString()).isEqualTo("Sara Socia");
        // FR-007: la validazione del capo è già data da chi iscrive.
        assertThat(obligation(enrollment, "LEADER_VALIDATION").get("status").asString()).isEqualTo("SATISFIED");
        assertThat(obligation(enrollment, "PAYMENT").get("status").asString()).isEqualTo("TODO");
        assertThat(actions(enrollment)).contains("WITHDRAW");
    }

    @Test
    void onlyScoutsOfTheLeadersOwnUnit() {
        String eventId = publish(scoutEvent()).get("id").asString();

        assertError(enrollFor("socio", "100007", eventId), 403, 40300);  // lupetto di un'altra unità
        assertError(enrollFor("rover", "100006", eventId), 403, 40300);  // un ragazzo non è capo
        assertError(enrollFor("capogruppo", "100006", eventId), 403, 40300); // capo di un'altra unità
        assertError(enrollFor("socio", "999999", eventId), 404, 40400);
    }

    @Test
    void eventMustAcceptLeaderEnrollments() {
        String selfOnly = publish(scoutEvent("enrollmentMode", "SELF")).get("id").asString();

        Result result = enrollFor("socio", "100006", selfOnly);

        assertError(result, 422, 42210);
        assertThat(fields(result.body().get("payload"))).containsExactly("enrollmentMode");
    }

    @Test
    void participantAndPresenterSeeTheEnrollmentOthersDoNot() {
        String eventId = publish(scoutEvent()).get("id").asString();
        String id = enrollFor("socio", "100006", eventId).body().get("payload").get("id").asString();

        assertThat(getEnrollment("esploratore", id).status()).isEqualTo(200);
        assertThat(getEnrollment("socio", id).status()).isEqualTo(200);
        assertError(getEnrollment("scolta", id), 404, 40400);
        assertError(enrollFor("socio", "100006", eventId), 409, 40911);
    }
}
