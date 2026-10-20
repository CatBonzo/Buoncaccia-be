package it.buonacaccia.v1.controller;

import static it.buonacaccia.v1.controller.EventManagementTest.assertError;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/** La segreteria allega file all'evento (programma, regolamento); tutti li scaricano a evento pubblicato. */
class EventAttachmentTest extends EnrollmentTestSupport {

    static final byte[] PDF = "%PDF-1.4\nprogramma\n%%EOF\n".getBytes();

    private static String path(String eventId) {
        return EVENTS + "/" + eventId + "/attachments";
    }

    private JsonNode attach(String eventId, String name) {
        Result result = postFile(path(eventId), tokenOf("segreteria"), name, "application/pdf", PDF);
        assertThat(result.status()).as("%s", result.body()).isEqualTo(201);
        return result.body().get("payload");
    }

    @Test
    void la_segreteria_allega_il_pubblico_elenca_e_scarica() {
        String eventId = publishOpen();
        JsonNode attachment = attach(eventId, "../programma.pdf");

        assertThat(attachment.get("name").asString()).isEqualTo("programma.pdf");
        JsonNode list = get(path(eventId)).body().get("payload");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("sizeBytes").asLong()).isEqualTo(PDF.length);

        RawResult raw = getBytes(path(eventId) + "/" + attachment.get("id").asString() + "/content", null);
        assertThat(raw.status()).isEqualTo(200);
        assertThat(raw.body()).isEqualTo(PDF);
        assertThat(raw.contentType()).startsWith("application/pdf");
        assertThat(raw.disposition()).contains("attachment").contains("programma.pdf");
    }

    @Test
    void la_bozza_ha_gli_allegati_solo_per_la_segreteria() {
        String eventId = post(EVENTS, tokenOf("segreteria"), openEvent()).body().get("payload").get("id").asString();
        attach(eventId, "bozza.pdf");

        assertThat(get(path(eventId), tokenOf("segreteria")).body().get("payload")).hasSize(1);
        assertError(get(path(eventId)), 404, 40400);
        assertError(get(path(eventId), tokenOf("rover")), 404, 40400);
    }

    @Test
    void solo_la_segreteria_dell_evento_carica_e_toglie() {
        String eventId = publishOpen();

        assertError(postFile(path(eventId), tokenOf("rover"), "a.pdf", "application/pdf", PDF), 403, 40300);
        assertThat(postFile(path(eventId), null, "a.pdf", "application/pdf", PDF).status()).isEqualTo(401);

        String id = attach(eventId, "a.pdf").get("id").asString();
        assertError(send("DELETE", path(eventId) + "/" + id, tokenOf("rover"), null), 403, 40300);
        assertThat(send("DELETE", path(eventId) + "/" + id, tokenOf("segreteria"), null).status()).isEqualTo(200);
        assertThat(get(path(eventId)).body().get("payload")).isEmpty();
        assertError(send("DELETE", path(eventId) + "/" + id, tokenOf("segreteria"), null), 404, 40400);
    }

    @Test
    void file_vuoti_di_altro_tipo_e_oltre_il_limite_sono_rifiutati() {
        String eventId = publishOpen();

        assertError(postFile(path(eventId), tokenOf("segreteria"), "v.pdf", "application/pdf", new byte[0]), 422, 42200);
        assertError(postFile(path(eventId), tokenOf("segreteria"), "x.pdf", "application/pdf", "<html></html>".getBytes()), 422, 42200);

        for (int i = 0; i < EventAttachmentServiceLimit.MAX; i++) {
            attach(eventId, "f" + i + ".pdf");
        }
        assertError(postFile(path(eventId), tokenOf("segreteria"), "uno.pdf", "application/pdf", PDF), 409, 40901);
    }

    /** Il limite per evento, ripetuto qui per non esporre il campo del servizio. */
    private static final class EventAttachmentServiceLimit {
        static final int MAX = 10;
    }
}
