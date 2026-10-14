package it.buonacaccia.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import it.buonacaccia.MailpitClient.Mail;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** US5 della 004: avviso a tutti gli iscritti attivi se cambiano date o luogo, o se l'evento è annullato (FR-012, FR-013). */
class EventChangeMailTest extends EnrollmentTestSupport {

    private static final List<String> PARTICIPANTS = List.of("rover", "scolta", "rover-lig");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Test
    void cambioDiLuogoAvvisaOgniIscrittoAttivo() {
        Map<String, Object> body = openEvent("seats", 2);
        JsonNode event = publish(body);
        PARTICIPANTS.forEach(p -> enrolled(p, event.get("id").asString()));

        Result updated = updateEvent(event, body, "location", "Rifugio Barbara");
        assertThat(updated.status()).as("%s", updated.body()).isEqualTo(200);

        for (String participant : PARTICIPANTS) {
            Mail mail = MAIL.expectOne(participant + "@buonacaccia.local", "Evento " + event.get("code").asString() + " modificato");
            assertThat(mail.text()).contains("Nuovo luogo: Rifugio Barbara").doesNotContain("Nuove date");
        }
    }

    @Test
    void cambioDiDateAvvisaConLeDateNuove() {
        Map<String, Object> body = openEvent();
        JsonNode event = publish(body);
        enrolled("rover", event.get("id").asString());

        Result updated = updateEvent(event, body, "startDate", today().plusDays(41).toString(),
                "endDate", today().plusDays(46).toString());
        assertThat(updated.status()).as("%s", updated.body()).isEqualTo(200);

        Mail mail = MAIL.expectOne("rover@buonacaccia.local", "Evento " + event.get("code").asString() + " modificato");
        assertThat(mail.text()).contains("Nuove date: dal " + DATE.format(today().plusDays(41))
                + " al " + DATE.format(today().plusDays(46)));
    }

    @Test
    void altreModificheNonMandanoAvvisi() {
        Map<String, Object> body = openEvent();
        JsonNode event = publish(body);
        enrolled("rover", event.get("id").asString());

        Result updated = updateEvent(event, body, "description", "Descrizione aggiornata", "seats", 50);
        assertThat(updated.status()).as("%s", updated.body()).isEqualTo(200);

        MAIL.assertStays("rover@buonacaccia.local", "Evento " + event.get("code").asString() + " modificato", 0,
                Duration.ofSeconds(2));
    }

    @Test
    void annullamentoAvvisaOgniIscrittoConMotivoERimborso() {
        JsonNode event = publish(openEvent("seats", 2));
        Map<String, JsonNode> enrollments = new HashMap<>();
        PARTICIPANTS.forEach(p -> enrollments.put(p, enrolled(p, event.get("id").asString())));

        Result cancelled = post(EVENTS + "/" + event.get("id").asString() + "/cancel", tokenOf("segreteria"),
                Map.of("reason", "Allerta meteo", "version", event.get("version").asLong()));
        assertThat(cancelled.status()).as("%s", cancelled.body()).isEqualTo(200);

        for (String participant : PARTICIPANTS) {
            JsonNode enrollment = enrollments.get(participant);
            Mail mail = MAIL.expectOne(participant + "@buonacaccia.local",
                    "Iscrizione " + enrollment.get("number").asString() + " annullata");
            assertThat(mail.text()).contains("l'evento è stato annullato. Motivo: Allerta meteo");
            if ("WAITING".equals(enrollment.get("position").asString())) {
                assertThat(mail.text()).contains("Non c'è nessuna quota da rimborsare");
            } else {
                assertThat(mail.text()).contains("La quota ti sarà rimborsata");
            }
        }
    }
}
