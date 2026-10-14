package it.buonacaccia.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import it.buonacaccia.MailpitClient.Mail;
import it.buonacaccia.service.EnrollmentJobs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** US4 della 004: un'e-mail a ogni cambio di stato dell'iscrizione, anche al presentatore (FR-011). */
class EnrollmentChangeMailTest extends EnrollmentTestSupport {

    private static final String ROVER = "rover@buonacaccia.local";
    private static final String SCOLTA = "scolta@buonacaccia.local";

    @Autowired
    EnrollmentJobs jobs;

    @Test
    void promozioneConPagamentoENuovaScadenzaERitiroConRimborso() {
        String eventId = publishOpen("seats", 1);
        JsonNode seated = enrolled("scolta", eventId);
        JsonNode waiting = enrolled("rover", eventId);

        assertThat(withdraw("scolta", seated).status()).isEqualTo(200);

        Mail promoted = MAIL.expectOne(ROVER, "Iscrizione " + waiting.get("number").asString() + ": un posto per te");
        assertThat(promoted.text()).contains("ora è nei posti").contains("quota di 150,00 €").contains("Da completare entro");
        Mail withdrawn = MAIL.expectOne(SCOLTA, "Iscrizione " + seated.get("number").asString() + " ritirata");
        assertThat(withdrawn.text()).contains("La quota ti sarà rimborsata");
    }

    @Test
    void ritiroAMenoDiSetteGiorniSenzaRimborso() {
        JsonNode enrollment = enrolled("rover", publishOpen());
        clock.setInstant(romeTime(today().plusDays(37), 10, 0));

        assertThat(withdraw("rover", enrollment).status()).isEqualTo(200);

        Mail mail = MAIL.expectOne(ROVER, "Iscrizione " + enrollment.get("number").asString() + " ritirata");
        assertThat(mail.text()).contains("Non è previsto il rimborso");
    }

    @Test
    void rifiutoConIlMotivo() {
        JsonNode enrollment = enrolled("rover", publishOpen());

        Result rejected = post(ENROLLMENTS + "/" + enrollment.get("id").asString()
                        + "/obligations/UNIT_LEADER_AUTHORIZATION/reject", tokenOf("capogruppo"),
                Map.of("reason", "Manca la preparazione tecnica"));
        assertThat(rejected.status()).as("%s", rejected.body()).isEqualTo(200);

        Mail mail = MAIL.expectOne(ROVER, "Iscrizione " + enrollment.get("number").asString() + " non accettata");
        assertThat(mail.text()).contains("Motivo: Manca la preparazione tecnica");
    }

    @Test
    void completamento() {
        JsonNode enrollment = enrolled("rover", publishOpen());
        for (JsonNode obligation : enrollment.get("obligations")) {
            if ("TODO".equals(obligation.get("status").asString())) {
                assertThat(post(ENROLLMENTS + "/" + enrollment.get("id").asString() + "/obligations/"
                        + obligation.get("type").asString() + "/satisfy", tokenOf("segreteria"), null).status())
                        .isEqualTo(200);
            }
        }

        Mail mail = MAIL.expectOne(ROVER, "Iscrizione " + enrollment.get("number").asString() + " completata");
        assertThat(mail.text()).contains("non resta nulla da fare");
    }

    @Test
    void scadenza() {
        clock.setInstant(Instant.now());
        JsonNode enrollment = enrolled("rover", publishOpen());
        clock.setInstant(Instant.parse(enrollment.get("deadline").asString()).plus(Duration.ofMinutes(1)));

        assertThat(jobs.expireOverdue()).isEqualTo(1);

        Mail mail = MAIL.expectOne(ROVER, "Iscrizione " + enrollment.get("number").asString() + " scaduta");
        assertThat(mail.text()).contains("non è stata completata entro la scadenza");
    }

    @Test
    void annullamentoPerPostiEsauritiAllaPartenza() {
        String eventId = publishOpen("seats", 1);
        enrolled("scolta", eventId);
        JsonNode waiting = enrolled("rover", eventId);
        clock.setInstant(romeTime(today().plusDays(40), 12, 0));

        assertThat(jobs.cancelWaitingAtStart()).isEqualTo(1);

        Mail mail = MAIL.expectOne(ROVER, "Iscrizione " + waiting.get("number").asString() + " annullata");
        assertThat(mail.text()).contains("non si è liberato nessun posto").contains("Non c'è nessuna quota da rimborsare");
    }

    @Test
    void ilPresentatoreRiceveGliStessiAvvisi() {
        String eventId = publish(scoutEvent()).get("id").asString();
        Result result = enrollFor("socio", "100006", eventId);
        assertThat(result.status()).as("%s", result.body()).isEqualTo(201);
        JsonNode enrollment = result.body().get("payload");

        assertThat(withdraw("socio", enrollment).status()).isEqualTo(200);

        String subject = "Iscrizione " + enrollment.get("number").asString() + " ritirata";
        MAIL.expectOne("esploratore@buonacaccia.local", subject);
        MAIL.expectOne("socio@buonacaccia.local", subject);
    }

    private Result withdraw(String username, JsonNode enrollment) {
        return post(ENROLLMENTS + "/" + enrollment.get("id").asString() + "/withdraw", tokenOf(username), null);
    }
}
