package it.buonacaccia.v1.controller;

import static it.buonacaccia.v1.controller.FormTemplateTest.question;
import static it.buonacaccia.v1.controller.FormTemplateTest.template;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import it.buonacaccia.model.FormSubmission;
import it.buonacaccia.model.FormTemplate;
import org.junit.jupiter.api.BeforeEach;
import tools.jackson.databind.JsonNode;

/** Base dei test della scheda: un modello per ROSS e SPEC_EG e un evento gratuito che la richiede. */
abstract class FormTestBase extends EnrollmentTestSupport {

    static final List<Map<String, Object>> FORM_ONLY =
            List.of(Map.of("type", "ELECTRONIC_FORM", "description", "Scheda elettronica"));

    @BeforeEach
    @SuppressWarnings("unchecked")
    void resetForms() {
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(), FormTemplate.class);
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(), FormSubmission.class);
        for (String type : List.of("ROSS", "SPEC_EG")) {
            put("/api/v1/event-types/" + type + "/form-template", tokenOf("amministratore"), template(
                    question("fede", "LONG_TEXT", true), question("anni", "TEXT", false),
                    question("allergie", "TEXT", false, "sensitive", true), question("ok", "YES_NO", true)));
        }
    }

    String formEvent() {
        return publish(openEvent("obligations", FORM_ONLY, "fee", 0)).get("id").asString();
    }

    static String form(String enrollmentId) {
        return ENROLLMENTS + "/" + enrollmentId + "/form";
    }

    static String id(JsonNode enrollment) {
        return enrollment.get("id").asString();
    }

    JsonNode formObligation(String username, String enrollmentId) {
        return obligation(enrollment(username, enrollmentId), "ELECTRONIC_FORM");
    }

    Result save(String username, String enrollmentId, Map<String, String> answers) {
        return put(form(enrollmentId), tokenOf(username), Map.of("answers", answers));
    }

    Result submit(String username, String enrollmentId) {
        return post(form(enrollmentId) + "/submit", tokenOf(username), null);
    }

    static final Map<String, String> COMPLETE = Map.of("fede", "Credo nel cammino", "ok", "true");
}
