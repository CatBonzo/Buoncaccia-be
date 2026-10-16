package it.buonacaccia.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** US4 della 007: la scheda la vedono solo il partecipante e la segreteria dell'evento. */
class FormAccessTest extends FormTestBase {

    @Test
    void la_segreteria_giusta_legge_solo_le_schede_inviate() {
        JsonNode enrollment = enrolled("rover", formEvent());
        save("rover", id(enrollment), COMPLETE);

        assertThat(get(form(id(enrollment)), tokenOf("segreteria")).status()).isEqualTo(404);

        submit("rover", id(enrollment));
        for (String user : new String[] {"segreteria", "amministratore", "rover"}) {
            Result result = get(form(id(enrollment)), tokenOf(user));
            assertThat(result.status()).as(user).isEqualTo(200);
            assertThat(result.body().get("payload").get("answers").get("fede").asString()).isEqualTo("Credo nel cammino");
        }
    }

    @Test
    void per_gli_altri_la_scheda_non_esiste() {
        JsonNode enrollment = enrolled("rover", formEvent());
        save("rover", id(enrollment), COMPLETE);
        submit("rover", id(enrollment));

        for (String user : new String[] {"scolta", "segreteria-lig", "capogruppo", "socio"}) {
            assertThat(get(form(id(enrollment)), tokenOf(user)).status()).as(user).isEqualTo(404);
        }
        assertThat(get(form(id(enrollment)), null).status()).isEqualTo(401);
    }

    @Test
    void il_presentatore_non_vede_la_scheda_del_ragazzo() {
        String eventId = publish(scoutEvent("obligations", FORM_ONLY, "fee", 0)).get("id").asString();
        JsonNode enrollment = enrollFor("socio", "100006", eventId).body().get("payload");

        assertThat(get(form(id(enrollment)), tokenOf("socio")).status()).isEqualTo(404);
        assertThat(save("socio", id(enrollment), COMPLETE).status()).isEqualTo(404);
        assertThat(get(form(id(enrollment)), tokenOf("esploratore")).status()).isEqualTo(200);
    }

    @Test
    void la_segreteria_non_compila_al_posto_del_partecipante() {
        JsonNode enrollment = enrolled("rover", formEvent());
        save("rover", id(enrollment), COMPLETE);
        submit("rover", id(enrollment));

        EventManagementTest.assertError(save("segreteria", id(enrollment), Map.of("fede", "x")), 403, 40300);
        assertThat(save("scolta", id(enrollment), Map.of("fede", "x")).status()).isEqualTo(404);
        assertThat(submit("segreteria-lig", id(enrollment)).status()).isEqualTo(404);
    }
}
