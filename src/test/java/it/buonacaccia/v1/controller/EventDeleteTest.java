package it.buonacaccia.v1.controller;

import static it.buonacaccia.v1.controller.EventManagementTest.assertError;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Un evento si elimina: la bozza sempre, uno creato solo se nessuno si è mai iscritto. */
class EventDeleteTest extends EnrollmentTestSupport {

    private Result delete(String user, String eventId) {
        return send("DELETE", EVENTS + "/" + eventId, user == null ? null : tokenOf(user), null);
    }

    private JsonNode draft() {
        Result result = post(EVENTS, tokenOf("segreteria"), openEvent());
        assertThat(result.status()).as("bozza: %s", result.body()).isEqualTo(201);
        return result.body().get("payload");
    }

    @Test
    void aDraftIsDeletedAndDisappears() {
        JsonNode draft = draft();
        assertThat(actions(draft)).contains("DELETE");

        assertThat(delete("segreteria", draft.get("id").asString()).status()).isEqualTo(200);

        assertError(get(EVENTS + "/" + draft.get("id").asString(), tokenOf("segreteria")), 404, 40400);
    }

    @Test
    void aCreatedEventWithoutEnrollmentsIsDeletedByItsSecretariatAndByAdmin() {
        String first = publishOpen();
        String second = publishOpen();
        assertThat(actions(get(EVENTS + "/" + first, tokenOf("segreteria")).body().get("payload"))).contains("DELETE");

        assertThat(delete("segreteria", first).status()).isEqualTo(200);
        assertThat(delete("amministratore", second).status()).isEqualTo(200);

        assertError(get(EVENTS + "/" + first), 404, 40400);
        assertError(get(EVENTS + "/" + second), 404, 40400);
    }

    @Test
    void aCreatedEventWithEnrollmentsCannotBeDeleted() {
        String eventId = publishOpen();
        enrolled("rover", eventId);

        assertError(delete("segreteria", eventId), 409, 40901);

        JsonNode event = get(EVENTS + "/" + eventId, tokenOf("segreteria")).body().get("payload");
        assertThat(actions(event)).doesNotContain("DELETE").contains("CANCEL");
    }

    @Test
    void aCancelledEventKeepsItsHistoryOnlyIfSomeoneWasEnrolled() {
        String withPeople = publishOpen();
        enrolled("rover", withPeople);
        String empty = publishOpen();
        for (String id : new String[] {withPeople, empty}) {
            JsonNode event = get(EVENTS + "/" + id, tokenOf("segreteria")).body().get("payload");
            Result cancelled = post(EVENTS + "/" + id + "/cancel", tokenOf("segreteria"),
                    Map.of("reason", "Maltempo", "version", event.get("version").asLong()));
            assertThat(cancelled.status()).as("annullo: %s", cancelled.body()).isEqualTo(200);
        }

        assertError(delete("segreteria", withPeople), 409, 40901);
        assertThat(delete("segreteria", empty).status()).isEqualTo(200);
    }

    @Test
    void onlyWhoManagesTheEventCanDeleteIt() {
        String eventId = publishOpen();

        assertError(delete(null, eventId), 401, 40100);
        assertError(delete("socio", eventId), 403, 40300);
        assertError(delete("segreteria-lig", eventId), 403, 40300);
        assertError(delete("segreteria", "inesistente"), 404, 40400);
        assertThat(get(EVENTS + "/" + eventId).status()).isEqualTo(200);
    }
}
