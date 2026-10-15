package it.buonacaccia.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** US1 della 006: l'evento dice quali documenti servono e con quali vincoli. */
class EventDocumentsTest extends EnrollmentTestSupport {

    private static Map<String, Object> document(String code, Object... extra) {
        Map<String, Object> obligation = new java.util.HashMap<>(Map.of("type", "DOCUMENT", "code", code,
                "description", "Documento " + code));
        for (int i = 0; i < extra.length; i += 2) {
            obligation.put((String) extra[i], extra[i + 1]);
        }
        return obligation;
    }

    private Result create(List<Map<String, Object>> obligations) {
        return post(EVENTS, tokenOf("segreteria"), openEvent("obligations", obligations));
    }

    @Test
    void due_documenti_con_vincoli_diventano_due_adempimenti_dell_iscrizione() {
        JsonNode event = publish(openEvent("obligations", List.of(
                document("FOTO", "allowedMimeTypes", List.of("image/jpeg"), "maxSizeBytes", 1_048_576),
                document("LIBERATORIA", "delivery", "PAPER"))));
        assertThat(event.get("obligations")).hasSize(2);

        JsonNode enrollment = enrolled("rover", event.get("id").asString());

        List<JsonNode> documents = enrollment.get("obligations").valueStream()
                .filter(o -> "DOCUMENT".equals(o.get("type").asString())).toList();
        assertThat(documents).extracting(o -> o.get("code").asString()).containsExactly("FOTO", "LIBERATORIA");
        assertThat(documents.get(0).get("maxSizeBytes").asLong()).isEqualTo(1_048_576L);
        assertThat(documents.get(0).get("allowedMimeTypes")).hasSize(1);
        assertThat(documents.get(1).get("delivery").asString()).isEqualTo("PAPER");
        assertThat(documents).allSatisfy(o -> assertThat(o.get("status").asString()).isEqualTo("TODO"));
    }

    @Test
    void senza_vincoli_valgono_i_predefiniti() {
        JsonNode event = publish(openEvent("obligations", List.of(Map.of("type", "DOCUMENT", "description", "Certificato"))));

        JsonNode document = obligation(enrolled("rover", event.get("id").asString()), "DOCUMENT");

        assertThat(document.get("code").asString()).isEqualTo("DOC");
        assertThat(document.get("delivery").asString()).isEqualTo("ONLINE");
        assertThat(document.get("maxSizeBytes").asLong()).isEqualTo(5L * 1024 * 1024);
        assertThat(document.get("allowedMimeTypes")).hasSize(3);
    }

    @Test
    void codici_duplicati_e_tipi_non_supportati_sono_rifiutati() {
        Result duplicated = create(List.of(document("FOTO"), document("FOTO")));
        EventManagementTest.assertError(duplicated, 422, 42200);
        assertThat(fields(duplicated.body().get("payload"))).contains("obligations[1].code");

        Result unsupported = create(List.of(document("FOTO", "allowedMimeTypes", List.of("application/zip"))));
        EventManagementTest.assertError(unsupported, 422, 42200);
        assertThat(fields(unsupported.body().get("payload"))).contains("obligations[0].allowedMimeTypes");
    }

    @Test
    void la_dimensione_massima_non_supera_10_mb() {
        Result result = create(List.of(document("FOTO", "maxSizeBytes", 10_485_761L)));

        EventManagementTest.assertError(result, 422, 42200);
        assertThat(fields(result.body().get("payload"))).contains("obligations[0].maxSizeBytes");
    }
}
