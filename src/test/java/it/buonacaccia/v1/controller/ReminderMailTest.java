package it.buonacaccia.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import it.buonacaccia.MailpitClient.Mail;
import it.buonacaccia.service.EnrollmentJobs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** US3 della 004: promemoria 3 giorni e 1 giorno prima della scadenza, una volta sola (FR-010). */
class ReminderMailTest extends EnrollmentTestSupport {

    private static final String ROVER = "rover@buonacaccia.local";
    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    @Autowired
    EnrollmentJobs jobs;

    @Test
    void promemoriaATreGiorniEAUnGiornoUnaVoltaCiascuno() {
        clock.setInstant(Instant.now());
        JsonNode enrollment = enrolled("rover", publishOpen());
        String number = enrollment.get("number").asString();
        Instant deadline = deadline(enrollment);

        clock.setInstant(deadline.atZone(ROME).minusDays(3).plusMinutes(1).toInstant());
        assertThat(jobs.sendReminders()).isGreaterThanOrEqualTo(1);
        Mail first = MAIL.expectOne(ROVER, "Promemoria: l'iscrizione " + number);
        assertThat(first.text()).contains("mancano 3 giorni").contains("autorizzazione come capo unità");
        jobs.sendReminders();
        MAIL.assertStays(ROVER, "Promemoria: l'iscrizione " + number, 1, Duration.ofSeconds(2));

        clock.setInstant(deadline.atZone(ROME).minusDays(1).plusMinutes(1).toInstant());
        jobs.sendReminders();
        assertThat(MAIL.expect(ROVER, "Promemoria: l'iscrizione " + number, 2))
                .anySatisfy(m -> assertThat(m.text()).contains("mancano 1 giorno"));
        jobs.sendReminders();
        MAIL.assertStays(ROVER, "Promemoria: l'iscrizione " + number, 2, Duration.ofSeconds(2));
    }

    @Test
    void seIlJobArrivaTardiParteSoloIlPromemoriaDiUnGiorno() {
        clock.setInstant(Instant.now());
        JsonNode enrollment = enrolled("rover", publishOpen());
        String number = enrollment.get("number").asString();

        clock.setInstant(deadline(enrollment).minus(Duration.ofHours(12)));
        jobs.sendReminders();
        jobs.sendReminders();

        Mail only = MAIL.expectOne(ROVER, "Promemoria: l'iscrizione " + number);
        assertThat(only.text()).contains("mancano 1 giorno");
        MAIL.assertStays(ROVER, "Promemoria: l'iscrizione " + number, 1, Duration.ofSeconds(2));
    }

    @Test
    void nessunPromemoriaPerIscrizioniCompleteORitirate() {
        clock.setInstant(Instant.now());
        String eventId = publishOpen();
        JsonNode completed = enrolled("rover", eventId);
        JsonNode withdrawn = enrolled("scolta", eventId);
        for (JsonNode obligation : completed.get("obligations")) {
            if ("TODO".equals(obligation.get("status").asString())) {
                Result satisfied = post(ENROLLMENTS + "/" + completed.get("id").asString() + "/obligations/"
                        + obligation.get("type").asString() + "/satisfy", tokenOf("segreteria"), null);
                assertThat(satisfied.status()).as("%s", satisfied.body()).isEqualTo(200);
            }
        }
        assertThat(enrollment("rover", completed.get("id").asString()).get("status").asString()).isEqualTo("COMPLETED");
        assertThat(post(ENROLLMENTS + "/" + withdrawn.get("id").asString() + "/withdraw", tokenOf("scolta"), null)
                .status()).isEqualTo(200);

        clock.setInstant(deadline(completed).minus(Duration.ofHours(12)));
        jobs.sendReminders();

        MAIL.assertStays(ROVER, "Promemoria: l'iscrizione " + completed.get("number").asString(), 0, Duration.ofSeconds(2));
        assertThat(MAIL.find("scolta@buonacaccia.local", "Promemoria: l'iscrizione " + withdrawn.get("number").asString()))
                .isEmpty();
    }

    @Test
    void dopoLaPromozioneIPromemoriaSeguonoLaNuovaScadenza() {
        Instant start = Instant.now();
        clock.setInstant(start);
        String eventId = publishOpen("seats", 1);
        JsonNode seated = enrolled("scolta", eventId);
        JsonNode waiting = enrolled("rover", eventId);
        String number = waiting.get("number").asString();

        clock.setInstant(start.plus(Duration.ofDays(2)));
        assertThat(post(ENROLLMENTS + "/" + seated.get("id").asString() + "/withdraw", tokenOf("scolta"), null)
                .status()).isEqualTo(200);
        Instant newDeadline = deadline(enrollment("rover", waiting.get("id").asString()));
        assertThat(newDeadline).isAfter(deadline(waiting));

        clock.setInstant(newDeadline.atZone(ROME).minusDays(3).plusMinutes(1).toInstant());
        jobs.sendReminders();

        Mail reminder = MAIL.expectOne(ROVER, "Promemoria: l'iscrizione " + number);
        assertThat(reminder.subject()).endsWith(DateTimeFormatter.ofPattern("dd/MM/yyyy").format(newDeadline.atZone(ROME)));
        assertThat(reminder.text()).contains("pagamento della quota");
    }

    private static Instant deadline(JsonNode enrollment) {
        return Instant.parse(enrollment.get("deadline").asString());
    }
}
