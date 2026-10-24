package it.buonacaccia.v1.controller;

import static it.buonacaccia.v1.controller.EventManagementTest.assertError;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import it.buonacaccia.service.EnrollmentJobs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** US3: pagina di stato, autorizzazioni, adempimenti della segreteria, completamento e scadenza. */
class EnrollmentLifecycleTest extends EnrollmentTestSupport {

    @Autowired
    private EnrollmentJobs jobs;

    private String path(String id, String type, String action) {
        return ENROLLMENTS + "/" + id + "/obligations/" + type + "/" + action;
    }

    @Test
    void statusPageIsVisibleOnlyToWhoIsInvolved() {
        String id = enrolled("rover", publishOpen()).get("id").asString();

        for (String user : new String[] {"rover", "capogruppo", "segreteria", "amministratore"}) {
            assertThat(getEnrollment(user, id).status()).as(user).isEqualTo(200);
        }
        for (String user : new String[] {"segreteria-lig", "scolta", "socio"}) {
            assertError(getEnrollment(user, id), 404, 40400);
        }
        assertError(get(ENROLLMENTS + "/" + id), 401, 40100);
    }

    @Test
    void availableActionsDependOnWhoAsks() {
        String id = enrolled("rover", publishOpen()).get("id").asString();

        assertThat(actions(enrollment("rover", id))).containsExactly("WITHDRAW");
        // Il capogruppo e' anche capo dell'unita' del rover: puo' ritirarlo e autorizzarlo (24/10).
        assertThat(actions(enrollment("capogruppo", id))).containsExactly("WITHDRAW", "APPROVE_UNIT_LEADER", "REJECT_UNIT_LEADER");
        assertThat(actions(enrollment("segreteria", id))).containsExactly("SATISFY");
    }

    @Test
    void unitLeaderAuthorizesAndSecretariatCompletes() {
        String id = enrolled("rover", publishOpen()).get("id").asString();
        JsonNode toAuthorize = get(ENROLLMENTS + "/to-authorize", tokenOf("capogruppo")).body().get("payload");
        assertThat(toAuthorize.get("content").valueStream().map(e -> e.get("id").asString())).contains(id);
        assertThat(get(ENROLLMENTS + "/to-authorize", tokenOf("socio")).body().get("payload").get("totalElements").asInt())
                .isZero();

        assertError(post(path(id, "UNIT_LEADER_AUTHORIZATION", "approve"), tokenOf("rover"), null), 403, 40300);
        assertError(post(path(id, "UNIT_LEADER_AUTHORIZATION", "approve"), tokenOf("socio"), null), 404, 40400);
        Result approved = post(path(id, "UNIT_LEADER_AUTHORIZATION", "approve"), tokenOf("capogruppo"), null);
        assertThat(approved.status()).isEqualTo(200);
        assertThat(obligation(approved.body().get("payload"), "UNIT_LEADER_AUTHORIZATION").get("status").asString())
                .isEqualTo("SATISFIED");
        assertError(post(path(id, "UNIT_LEADER_AUTHORIZATION", "approve"), tokenOf("capogruppo"), null), 409, 40910);

        assertError(post(path(id, "DOCUMENT", "satisfy"), tokenOf("segreteria-lig"), Map.of("note", "ok")), 403, 40300);
        assertError(post(path(id, "DOCUMENT", "satisfy"), tokenOf("rover"), null), 403, 40300);
        JsonNode document = post(path(id, "DOCUMENT", "satisfy"), tokenOf("segreteria"), Map.of("note", "Ricevuta a mano"))
                .body().get("payload");
        assertThat(obligation(document, "DOCUMENT").get("note").asString()).isEqualTo("Ricevuta a mano");
        assertThat(document.get("status").asString()).isEqualTo("REGISTERED");

        // FR-011: l'ultimo adempimento completa l'iscrizione.
        JsonNode completed = post(path(id, "PAYMENT", "satisfy"), tokenOf("segreteria"), null).body().get("payload");
        assertThat(completed.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(completed.get("completedAt").isNull()).isFalse();
    }

    @Test
    void groupLeaderRejectionFreesTheSeatForTheFirstWaiting() {
        String eventId = publish(leaderEvent("seats", 1)).get("id").asString();
        String socio = enrolled("socio", eventId).get("id").asString();
        String capogruppo = enrolled("capogruppo", eventId).get("id").asString();
        assertThat(enrollment("capogruppo", capogruppo).get("position").asString()).isEqualTo("WAITING");

        assertError(post(path(socio, "GROUP_LEADER_AUTHORIZATION", "reject"), tokenOf("capogruppo"), Map.of()), 422, 42200);
        Result rejected = post(path(socio, "GROUP_LEADER_AUTHORIZATION", "reject"), tokenOf("capogruppo"),
                Map.of("reason", "Non ha ancora finito il tirocinio"));

        assertThat(rejected.status()).isEqualTo(200);
        JsonNode closed = rejected.body().get("payload");
        assertThat(closed.get("status").asString()).isEqualTo("REJECTED");
        assertThat(closed.get("closingReason").asString()).isEqualTo("REJECTED");
        assertThat(closed.get("closingNote").asString()).isEqualTo("Non ha ancora finito il tirocinio");
        assertThat(closed.get("active").asBoolean()).isFalse();
        JsonNode promoted = enrollment("capogruppo", capogruppo);
        assertThat(promoted.get("position").asString()).isEqualTo("IN_SEATS");
        assertThat(obligation(promoted, "PAYMENT").get("status").asString()).isEqualTo("TODO");
    }

    @Test
    void overdueEnrollmentExpiresAndTheCompletedWaitingOneGetsPayment() {
        String eventId = publishOpen("seats", 1);
        String rover = enrolled("rover", eventId).get("id").asString();
        String scolta = enrolled("scolta", eventId).get("id").asString();
        // Chi aspetta completa gli altri adempimenti: senza pagamento diventa COMPLETA (FR-017).
        post(path(scolta, "UNIT_LEADER_AUTHORIZATION", "approve"), tokenOf("capogruppo"), null);
        JsonNode waitingCompleted = post(path(scolta, "DOCUMENT", "satisfy"), tokenOf("segreteria"), null).body().get("payload");
        assertThat(waitingCompleted.get("status").asString()).isEqualTo("COMPLETED");

        // +2 ore e non +1 minuto: la scadenza e' a giorni di calendario e il 25 ottobre il cambio dell'ora la sposta di un'ora.
        Instant later = Instant.now().plus(Duration.ofDays(9)).plus(Duration.ofHours(2))
                .truncatedTo(java.time.temporal.ChronoUnit.MILLIS); // MongoDB salva i millisecondi
        clock.setInstant(later);
        assertThat(jobs.expireOverdue()).isEqualTo(1);

        JsonNode expired = enrollment("rover", rover);
        assertThat(expired.get("status").asString()).isEqualTo("EXPIRED");
        assertThat(expired.get("closingReason").asString()).isEqualTo("EXPIRED");
        assertThat(expired.get("refundDue").asBoolean()).isFalse();
        JsonNode promoted = enrollment("scolta", scolta);
        assertThat(promoted.get("position").asString()).isEqualTo("IN_SEATS");
        assertThat(promoted.get("status").asString()).isEqualTo("REGISTERED");
        assertThat(obligation(promoted, "PAYMENT").get("status").asString()).isEqualTo("TODO");
        assertThat(Instant.parse(promoted.get("deadline").asString()))
                .isEqualTo(later.atZone(java.time.ZoneId.of("Europe/Rome")).plusDays(9).toInstant());
        // Il giro successivo non trova altro da fare.
        assertThat(jobs.expireOverdue()).isZero();
    }
}
