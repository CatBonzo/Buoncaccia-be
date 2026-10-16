package it.buonacaccia.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import it.buonacaccia.model.FormTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** US1 della 007: l'amministrazione definisce il modello di scheda di un tipo evento. */
class FormTemplateTest extends EnrollmentTestSupport {

    static final String TEMPLATE = "/api/v1/event-types/ROSS/form-template";

    @BeforeEach
    void clearTemplates() {
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(), FormTemplate.class);
    }

    static Map<String, Object> question(String id, String type, boolean required, Object... extra) {
        Map<String, Object> q = new java.util.HashMap<>(Map.of("id", id, "text", "Domanda " + id, "type", type,
                "required", required, "sensitive", false));
        for (int i = 0; i < extra.length; i += 2) {
            q.put((String) extra[i], extra[i + 1]);
        }
        return q;
    }

    static Map<String, Object> template(Map<String, Object>... questions) {
        return Map.of("sections", List.of(Map.of("title", "Cammino", "questions", List.of(questions))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void l_amministrazione_salva_rilegge_e_sostituisce() {
        Result saved = put(TEMPLATE, tokenOf("amministratore"), template(
                question("fede", "LONG_TEXT", true), question("allergie", "TEXT", false, "sensitive", true)));

        assertThat(saved.status()).as("%s", saved.body()).isEqualTo(200);
        JsonNode payload = get(TEMPLATE, tokenOf("rover")).body().get("payload");
        assertThat(payload.get("typeCode").asString()).isEqualTo("ROSS");
        JsonNode questions = payload.get("sections").get(0).get("questions");
        assertThat(questions).hasSize(2);
        assertThat(questions.get(1).get("sensitive").asBoolean()).isTrue();

        put(TEMPLATE, tokenOf("amministratore"), template(question("nuova", "YES_NO", false)));
        assertThat(get(TEMPLATE, tokenOf("rover")).body().get("payload").get("sections").get(0).get("questions")).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void modelli_non_validi_sono_rifiutati_con_i_campi() {
        Result duplicated = put(TEMPLATE, tokenOf("amministratore"),
                template(question("a", "TEXT", false), question("a", "TEXT", false)));
        EventManagementTest.assertError(duplicated, 422, 42200);
        assertThat(fields(duplicated.body().get("payload"))).containsExactly("sections[0].questions[1].id");

        Result choice = put(TEMPLATE, tokenOf("amministratore"), template(question("c", "CHOICE", false, "options", List.of("sola"))));
        assertThat(fields(choice.body().get("payload"))).containsExactly("sections[0].questions[0].options");

        EventManagementTest.assertError(put(TEMPLATE, tokenOf("amministratore"), Map.of("sections", List.of())), 422, 42200);
        EventManagementTest.assertError(put(TEMPLATE, tokenOf("amministratore"), template(question("x", "BOH", false))), 422, 42200);
    }

    @Test
    @SuppressWarnings("unchecked")
    void solo_l_amministrazione_scrive_e_il_tipo_deve_esistere() {
        EventManagementTest.assertError(put(TEMPLATE, tokenOf("segreteria"), template(question("a", "TEXT", false))), 403, 40300);
        EventManagementTest.assertError(put("/api/v1/event-types/NOPE/form-template", tokenOf("amministratore"),
                template(question("a", "TEXT", false))), 404, 40400);
        EventManagementTest.assertError(get(TEMPLATE, tokenOf("rover")), 404, 40400);
        assertThat(get(TEMPLATE, null).status()).isEqualTo(401);
    }

    @Test
    @SuppressWarnings("unchecked")
    void un_evento_con_la_scheda_vuole_il_modello_del_suo_tipo() {
        Map<String, Object> body = openEvent("obligations", List.of(Map.of("type", "ELECTRONIC_FORM", "description", "Scheda")));

        Result without = post(EVENTS, tokenOf("segreteria"), body);
        EventManagementTest.assertError(without, 422, 42200);
        assertThat(fields(without.body().get("payload"))).containsExactly("obligations");

        put(TEMPLATE, tokenOf("amministratore"), template(question("a", "TEXT", false)));
        assertThat(post(EVENTS, tokenOf("segreteria"), body).status()).isEqualTo(201);
    }
}
