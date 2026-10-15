package it.buonacaccia.v1.controller;

import static it.buonacaccia.v1.controller.EventManagementTest.assertError;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import it.buonacaccia.MailpitClient.Mail;
import it.buonacaccia.model.Payment;
import it.buonacaccia.model.Refund;
import it.buonacaccia.service.EnrollmentJobs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** US3: il rimborso nasce alla chiusura dell'iscrizione e la segreteria lo lavora (FR-009…FR-013 della 005). */
class RefundFlowTest extends EnrollmentTestSupport {

    private static final String REFUNDS = "/api/v1/refunds";
    private static final String ROVER = "rover@buonacaccia.local";

    @Autowired
    EnrollmentJobs jobs;

    @BeforeEach
    void clearMoney() {
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(), Payment.class);
        mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(), Refund.class);
    }

    private void pay(JsonNode enrollment, Object amount) {
        post(ENROLLMENTS + "/" + enrollment.get("id").asString() + "/obligations/UNIT_LEADER_AUTHORIZATION/satisfy",
                tokenOf("segreteria"), null);
        Result result = post(ENROLLMENTS + "/" + enrollment.get("id").asString() + "/payments", tokenOf("segreteria"),
                Map.of("amount", amount, "method", "BANK_TRANSFER", "receivedOn", today().toString(),
                        "reference", "R-" + System.nanoTime()));
        assertThat(result.status()).as("pagamento: %s", result.body()).isEqualTo(201);
    }

    private Result withdraw(String user, JsonNode enrollment) {
        return post(ENROLLMENTS + "/" + enrollment.get("id").asString() + "/withdraw", tokenOf(user), null);
    }

    private JsonNode refundsOf(String eventId, String user) {
        Result result = get(REFUNDS + "?eventId=" + eventId, tokenOf(user));
        assertThat(result.status()).as("coda: %s", result.body()).isEqualTo(200);
        return result.body().get("payload").get("content");
    }

    /** Ritiro di un iscritto che ha pagato 150 e rimborso richiesto: restituisce il rimborso. */
    private JsonNode requestedRefund(String eventId) {
        JsonNode enrollment = enrolled("rover", eventId);
        pay(enrollment, 150);
        assertThat(withdraw("rover", enrollment).status()).isEqualTo(200);
        JsonNode refunds = refundsOf(eventId, "segreteria");
        assertThat(refunds.size()).isEqualTo(1);
        return refunds.get(0);
    }

    @Test
    void ilRitiroConPagamentoPrimaDeiSetteGiorniApreUnRimborsoDelVersato() {
        String eventId = publishOpen();
        JsonNode enrollment = enrolled("rover", eventId);
        pay(enrollment, 150);

        assertThat(withdraw("rover", enrollment).status()).isEqualTo(200);

        JsonNode refund = refundsOf(eventId, "segreteria").get(0);
        assertThat(refund.get("status").asString()).isEqualTo("REQUESTED");
        assertThat(refund.get("amount").asDouble()).isEqualTo(150.0);
        assertThat(refund.get("enrollmentNumber").asString()).isEqualTo(enrollment.get("number").asString());
        assertThat(enrollment("rover", enrollment.get("id").asString()).get("refund").get("status").asString())
                .isEqualTo("REQUESTED");
        Mail mail = MAIL.expectOne(ROVER, "Rimborso richiesto per l'iscrizione " + enrollment.get("number").asString());
        assertThat(mail.text()).contains("150,00 €");
    }

    @Test
    void ilRitiroATreGiorniDallaPartenzaNonApreNulla() {
        String eventId = publishOpen();
        JsonNode enrollment = enrolled("rover", eventId);
        pay(enrollment, 150);
        clock.setInstant(romeTime(today().plusDays(37), 10, 0));

        assertThat(withdraw("rover", enrollment).status()).isEqualTo(200);

        assertThat(refundsOf(eventId, "segreteria").size()).isZero();
        assertThat(enrollment("rover", enrollment.get("id").asString()).get("refund").isNull()).isTrue();
    }

    @Test
    void senzaPagamentiNonCeNulloDaRimborsare() {
        String eventId = publishOpen();
        JsonNode enrollment = enrolled("rover", eventId);

        assertThat(withdraw("rover", enrollment).status()).isEqualTo(200);

        assertThat(refundsOf(eventId, "segreteria").size()).isZero();
    }

    @Test
    void laScadenzaConUnPagamentoParzialeNonRimborsaNulla() {
        String eventId = publishOpen();
        clock.setInstant(Instant.now());
        JsonNode enrollment = enrolled("rover", eventId);
        pay(enrollment, 50);
        clock.setInstant(Instant.parse(enrollment.get("deadline").asString()).plus(Duration.ofMinutes(1)));

        assertThat(jobs.expireOverdue()).isGreaterThanOrEqualTo(1);

        assertThat(refundsOf(eventId, "segreteria").size()).isZero();
        assertThat(enrollment("rover", enrollment.get("id").asString()).get("status").asString()).isEqualTo("EXPIRED");
    }

    @Test
    void lEventoAnnullatoRimborsaSoloChiHaPagatoNeiPosti() {
        JsonNode event = publish(openEvent("seats", 2));
        String eventId = event.get("id").asString();
        JsonNode paid = enrolled("rover", eventId);
        enrolled("scolta", eventId);
        enrolled("rover-lig", eventId);
        pay(paid, 150);

        Result cancelled = post(EVENTS + "/" + eventId + "/cancel", tokenOf("segreteria"),
                Map.of("reason", "Maltempo", "version", event.get("version").asLong()));
        assertThat(cancelled.status()).as("%s", cancelled.body()).isEqualTo(200);

        JsonNode refunds = refundsOf(eventId, "segreteria");
        assertThat(refunds.size()).isEqualTo(1);
        assertThat(refunds.get(0).get("enrollmentNumber").asString()).isEqualTo(paid.get("number").asString());
        assertThat(refunds.get(0).get("closingReason").asString()).isEqualTo("EVENT_CANCELLED");
    }

    @Test
    void presaInCaricoEmessoERifiutatoPassanoPerGliStatiGiusti() {
        JsonNode refund = requestedRefund(publishOpen());
        String id = refund.get("id").asString();
        String number = refund.get("enrollmentNumber").asString();

        Result taken = post(REFUNDS + "/" + id + "/take", tokenOf("segreteria"), null);
        assertThat(taken.body().get("payload").get("status").asString()).isEqualTo("IN_PROGRESS");

        assertError(post(REFUNDS + "/" + id + "/issue", tokenOf("segreteria"),
                Map.of("issuedOn", today().plusDays(1).toString())), 422, 42200);
        Result issued = post(REFUNDS + "/" + id + "/issue", tokenOf("segreteria"),
                Map.of("issuedOn", today().toString(), "reference", "BON-77"));
        assertThat(issued.status()).as("%s", issued.body()).isEqualTo(200);
        assertThat(issued.body().get("payload").get("status").asString()).isEqualTo("ISSUED");
        assertThat(issued.body().get("payload").get("reference").asString()).isEqualTo("BON-77");
        Mail mail = MAIL.expectOne(ROVER, "Rimborso emesso per l'iscrizione " + number);
        assertThat(mail.text()).contains("BON-77").contains("150,00 €");

        assertError(post(REFUNDS + "/" + id + "/take", tokenOf("segreteria"), null), 409, 40940);
        assertError(post(REFUNDS + "/" + id + "/reject", tokenOf("segreteria"), Map.of("reason", "x")), 409, 40940);
        assertError(post(REFUNDS + "/" + id + "/issue", tokenOf("segreteria"),
                Map.of("issuedOn", today().toString())), 409, 40940);
    }

    @Test
    void ilRifiutoVuoleIlMotivoEMandaLEmail() {
        JsonNode refund = requestedRefund(publishOpen());
        String id = refund.get("id").asString();

        assertError(post(REFUNDS + "/" + id + "/reject", tokenOf("segreteria"), Map.of()), 422, 42200);
        Result rejected = post(REFUNDS + "/" + id + "/reject", tokenOf("segreteria"), Map.of("reason", "Ritiro tardivo"));

        assertThat(rejected.body().get("payload").get("status").asString()).isEqualTo("REJECTED");
        assertThat(rejected.body().get("payload").get("rejectReason").asString()).isEqualTo("Ritiro tardivo");
        Mail mail = MAIL.expectOne(ROVER, "Rimborso non accordato per l'iscrizione " + refund.get("enrollmentNumber").asString());
        assertThat(mail.text()).contains("Motivo: Ritiro tardivo");
    }

    @Test
    void laCodaSiFiltraPerStatoEPerRegione() {
        String eventId = publishOpen();
        JsonNode refund = requestedRefund(eventId);
        String id = refund.get("id").asString();

        assertThat(get(REFUNDS + "?status=REQUESTED&eventId=" + eventId, tokenOf("segreteria"))
                .body().get("payload").get("content").size()).isEqualTo(1);
        assertThat(get(REFUNDS + "?status=ISSUED&eventId=" + eventId, tokenOf("segreteria"))
                .body().get("payload").get("content").size()).isZero();
        assertThat(refundsOf(eventId, "segreteria-lig").size()).isZero();
        assertThat(refundsOf(eventId, "amministratore").size()).isEqualTo(1);
        assertError(post(REFUNDS + "/" + id + "/take", tokenOf("segreteria-lig"), null), 404, 40400);
        assertError(get(REFUNDS, tokenOf("rover")), 403, 40300);
        assertError(get(REFUNDS, null), 401, 40100);
    }

    @Test
    void dueSegreterieCheLoPrendonoInCaricoInsiemeRiesceSoloUna() throws Exception {
        JsonNode refund = requestedRefund(publishOpen());
        String path = REFUNDS + "/" + refund.get("id").asString() + "/take";
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Result>> tasks = List.of(() -> post(path, tokenOf("segreteria"), null),
                    () -> post(path, tokenOf("amministratore"), null));
            int ok = 0;
            int conflict = 0;
            for (Future<Result> future : pool.invokeAll(tasks)) {
                int status = future.get().status();
                ok += status == 200 ? 1 : 0;
                conflict += status == 409 ? 1 : 0;
            }
            assertThat(ok).isEqualTo(1);
            assertThat(conflict).isEqualTo(1);
        } finally {
            pool.shutdown();
        }
    }
}
