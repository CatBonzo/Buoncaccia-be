package it.buonacaccia.v1.controller;

import static it.buonacaccia.v1.controller.EventManagementTest.assertError;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** 24/10: le iscrizioni di L/C ed E/G le gestisce solo il capo unità (presentatore); il ragazzo le vede soltanto. */
class LeaderManagedEnrollmentTest extends FormTestBase {

    private String enrolledByLeader(String eventId) {
        Result result = enrollFor("socio", "100006", eventId);
        assertThat(result.status()).as("%s", result.body()).isEqualTo(201);
        return id(result.body().get("payload"));
    }

    @Test
    void ilRagazzoEg_VedeMaNonPagaNeSiRitira_IlCapoUnitaSi() {
        String id = enrolledByLeader(publish(scoutEvent()).get("id").asString());
        String card = ENROLLMENTS + "/" + id + "/card-payment";

        assertThat(getEnrollment("esploratore", id).status()).isEqualTo(200);
        assertError(post(card, tokenOf("esploratore"), Map.of("cardLast4", "4242")), 403, 40300);
        assertError(post(ENROLLMENTS + "/" + id + "/withdraw", tokenOf("esploratore"), null), 403, 40300);
        assertThat(actions(enrollment("esploratore", id))).doesNotContain("WITHDRAW");

        assertThat(actions(enrollment("socio", id))).contains("WITHDRAW");
        assertThat(post(card, tokenOf("socio"), Map.of("cardLast4", "4242")).status()).isEqualTo(201);
    }

    @Test
    void ChiNonCentraNonVedeNienteELaScheda_LaCompilaIlCapoUnita() {
        String eventId = publish(openEvent("typeCode", "CAPISQ", "enrollmentMode", "BY_LEADER", "fee", 0,
                "requirements", scoutEvent().get("requirements"), "obligations", FORM_ONLY)).get("id").asString();
        String id = enrolledByLeader(eventId);

        assertThat(get(form(id), tokenOf("esploratore")).status()).isEqualTo(200);
        assertError(save("esploratore", id, COMPLETE), 403, 40300);
        assertError(submit("esploratore", id), 403, 40300);
        assertError(get(form(id), tokenOf("scolta")), 404, 40400);

        assertThat(save("socio", id, COMPLETE).status()).isEqualTo(200);
        assertThat(submit("socio", id).status()).isEqualTo(200);
        JsonNode after = enrollment("socio", id);
        assertThat(obligation(after, "ELECTRONIC_FORM").get("status").asString()).isIn("IN_REVIEW", "SATISFIED");
    }

    @Test
    void ilCapoGruppoDelGruppoNonPagaAlPostoDelCapoUnita() {
        String id = enrolledByLeader(publish(scoutEvent()).get("id").asString());

        assertError(post(ENROLLMENTS + "/" + id + "/card-payment", tokenOf("capogruppo"), Map.of("cardLast4", "4242")),
                404, 40400);
    }
}
