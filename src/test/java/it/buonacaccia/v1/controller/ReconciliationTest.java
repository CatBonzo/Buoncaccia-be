package it.buonacaccia.v1.controller;

import static it.buonacaccia.v1.controller.EventManagementTest.assertError;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.Map;

import it.buonacaccia.model.Payment;
import it.buonacaccia.model.Refund;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** US4: quadro dei pagamenti di un evento e CSV (FR-014, FR-015, SC-004 della 005). */
class ReconciliationTest extends EnrollmentTestSupport {

    @BeforeEach
    void clearMoney() {
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(), Payment.class);
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(), Refund.class);
    }

    private Result pay(JsonNode enrollment, Object amount, String reference) {
        post(ENROLLMENTS + "/" + enrollment.get("id").asString() + "/obligations/UNIT_LEADER_AUTHORIZATION/satisfy",
                tokenOf("segreteria"), null);
        return post(ENROLLMENTS + "/" + enrollment.get("id").asString() + "/payments", tokenOf("segreteria"),
                Map.of("amount", amount, "method", "BANK_TRANSFER", "receivedOn", today().toString(), "reference", reference));
    }

    private Result quadro(String eventId, String user) {
        return get(EVENTS + "/" + eventId + "/reconciliation", user == null ? null : tokenOf(user));
    }

    @Test
    void totaliIscrizioniConResiduoEInvarianteDelleSomme() {
        String eventId = publishOpen();
        JsonNode rover = enrolled("rover", eventId);
        JsonNode scolta = enrolled("scolta", eventId);
        JsonNode roverLig = enrolled("rover-lig", eventId);
        pay(rover, 150, "A");
        pay(scolta, 50, "B");
        String wrong = pay(roverLig, 20, "C").body().get("payload").get("id").asString();
        post("/api/v1/payments/" + wrong + "/cancel", tokenOf("segreteria"), Map.of("reason", "Sbagliato"));
        post(ENROLLMENTS + "/" + rover.get("id").asString() + "/withdraw", tokenOf("rover"), null);
        String refundId = get("/api/v1/refunds?eventId=" + eventId, tokenOf("segreteria")).body().get("payload")
                .get("content").get(0).get("id").asString();
        post("/api/v1/refunds/" + refundId + "/issue", tokenOf("segreteria"), Map.of("issuedOn", today().toString()));

        Result result = quadro(eventId, "segreteria");

        assertThat(result.status()).as("%s", result.body()).isEqualTo(200);
        JsonNode payload = result.body().get("payload");
        assertThat(payload.get("collected").asDouble()).isEqualTo(200.0);
        assertThat(payload.get("cancelled").asDouble()).isEqualTo(20.0);
        assertThat(payload.get("toRefund").asDouble()).isZero();
        assertThat(payload.get("refunded").asDouble()).isEqualTo(150.0);
        assertThat(payload.get("payments").size()).isEqualTo(3);
        assertThat(payload.get("refunds").size()).isEqualTo(1);
        // SC-004: incassato - rimborsato = pagamenti validi - rimborsi emessi.
        double validPayments = 0;
        for (JsonNode payment : payload.get("payments")) {
            validPayments += "VALID".equals(payment.get("status").asString()) ? payment.get("amount").asDouble() : 0;
        }
        assertThat(payload.get("collected").asDouble() - payload.get("refunded").asDouble())
                .isEqualTo(validPayments - payload.get("refunds").get(0).get("amount").asDouble());
        // Resta da incassare: scolta ha versato 50 su 150, rover-lig 0 su 150 (il pagamento annullato); rover si è ritirato.
        assertThat(payload.get("outstanding").size()).isEqualTo(2);
        JsonNode partial = payload.get("outstanding").valueStream()
                .filter(o -> scolta.get("number").asString().equals(o.get("enrollmentNumber").asString())).findFirst().orElseThrow();
        assertThat(partial.get("due").asDouble()).isEqualTo(100.0);
        // L'id serve a chi registra il bonifico senza cercare l'iscrizione per numero (feature 011).
        assertThat(partial.get("enrollmentId").asString()).isEqualTo(scolta.get("id").asString());
    }

    @Test
    void ilCsvHaLeStesseRighe() {
        String eventId = publishOpen();
        JsonNode rover = enrolled("rover", eventId);
        pay(rover, 150, "CRO-1");
        post(ENROLLMENTS + "/" + rover.get("id").asString() + "/withdraw", tokenOf("rover"), null);

        HttpResponse<String> response = getRaw(EVENTS + "/" + eventId + "/reconciliation/export", tokenOf("segreteria"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("text/csv");
        assertThat(response.headers().firstValue("Content-Disposition").orElse("")).contains("attachment").contains(".csv");
        var lines = response.body().lines().toList();
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).startsWith("tipo;iscrizione;");
        assertThat(lines.get(1)).startsWith("PAGAMENTO;" + rover.get("number").asString() + ";").contains(";150.00;").contains("CRO-1");
        assertThat(lines.get(2)).startsWith("RIMBORSO;").contains(";150.00;").contains("REQUESTED");
    }

    @Test
    void soloChiGestisceLEventoEdEsisteLEvento() {
        String eventId = publishOpen();

        assertError(quadro(eventId, "segreteria-lig"), 403, 40300);
        assertError(quadro(eventId, "rover"), 403, 40300);
        assertError(quadro(eventId, null), 401, 40100);
        assertError(quadro("inesistente", "segreteria"), 404, 40400);
        assertThat(quadro(eventId, "amministratore").status()).isEqualTo(200);
    }
}
