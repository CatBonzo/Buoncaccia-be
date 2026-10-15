package it.buonacaccia.v1.controller;

import static it.buonacaccia.v1.controller.DocumentUploadTest.JPEG_HEAD;
import static it.buonacaccia.v1.controller.DocumentUploadTest.PNG_HEAD;
import static it.buonacaccia.v1.controller.DocumentUploadTest.file;
import static it.buonacaccia.v1.controller.DocumentUploadTest.id;
import static it.buonacaccia.v1.controller.DocumentUploadTest.upload;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** US4 della 006: i file li vedono solo partecipante, presentatore e segreteria che gestisce l'evento. */
class DocumentAccessTest extends EnrollmentTestSupport {

    private JsonNode enrollmentWithTwoUploads(String[] ids) {
        String eventId = publish(openEvent("obligations", List.of(
                Map.of("type", "DOCUMENT", "code", "FOTO", "description", "Foto")))).get("id").asString();
        JsonNode enrollment = enrolled("rover", eventId);
        ids[0] = postFile(upload(id(enrollment), "FOTO"), tokenOf("rover"), "prima.jpg", "image/jpeg", file(JPEG_HEAD, 120))
                .body().get("payload").get("id").asString();
        ids[1] = postFile(upload(id(enrollment), "FOTO"), tokenOf("rover"), "seconda.png", "image/png", file(PNG_HEAD, 240))
                .body().get("payload").get("id").asString();
        return enrollment;
    }

    @Test
    void partecipante_e_segreteria_della_regione_vedono_elenco_e_file() {
        String[] ids = new String[2];
        JsonNode enrollment = enrollmentWithTwoUploads(ids);

        for (String user : List.of("rover", "segreteria", "amministratore")) {
            Result list = get(ENROLLMENTS + "/" + id(enrollment) + "/documents", tokenOf(user));
            assertThat(list.status()).as(user).isEqualTo(200);
            assertThat(list.body().get("payload")).as(user).hasSize(2);
        }
        RawResult replaced = getBytes("/api/v1/documents/" + ids[0] + "/content", tokenOf("segreteria"));
        assertThat(replaced.status()).isEqualTo(200);
        assertThat(replaced.contentType()).isEqualTo("image/jpeg");
        assertThat(replaced.disposition()).contains("attachment").contains("prima.jpg");
        assertThat(replaced.body()).isEqualTo(file(JPEG_HEAD, 120));

        RawResult current = getBytes("/api/v1/documents/" + ids[1] + "/content", tokenOf("rover"));
        assertThat(current.contentType()).isEqualTo("image/png");
        assertThat(current.body()).isEqualTo(file(PNG_HEAD, 240));
    }

    @Test
    void il_presentatore_vede_i_documenti_del_ragazzo() {
        String eventId = publish(scoutEvent("obligations", List.of(
                Map.of("type", "DOCUMENT", "code", "FOTO", "description", "Foto")))).get("id").asString();
        JsonNode enrollment = enrollFor("socio", "100006", eventId).body().get("payload");
        String documentId = postFile(upload(id(enrollment), "FOTO"), tokenOf("socio"), "f.jpg", "image/jpeg", file(JPEG_HEAD, 50))
                .body().get("payload").get("id").asString();

        assertThat(get(ENROLLMENTS + "/" + id(enrollment) + "/documents", tokenOf("socio")).status()).isEqualTo(200);
        assertThat(getBytes("/api/v1/documents/" + documentId + "/content", tokenOf("socio")).status()).isEqualTo(200);
    }

    @Test
    void per_gli_altri_il_documento_non_esiste() {
        String[] ids = new String[2];
        JsonNode enrollment = enrollmentWithTwoUploads(ids);

        for (String user : List.of("scolta", "segreteria-lig", "capogruppo")) {
            assertThat(get(ENROLLMENTS + "/" + id(enrollment) + "/documents", tokenOf(user)).status()).as(user).isEqualTo(404);
            assertThat(getBytes("/api/v1/documents/" + ids[1] + "/content", tokenOf(user)).status()).as(user).isEqualTo(404);
        }
        assertThat(getBytes("/api/v1/documents/" + ids[1] + "/content", null).status()).isEqualTo(401);
        assertThat(getBytes("/api/v1/documents/inesistente/content", tokenOf("rover")).status()).isEqualTo(404);
    }
}
