package it.buonacaccia.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;

import it.buonacaccia.MailpitClient.Mail;
import it.buonacaccia.model.Notification;
import it.buonacaccia.model.NotificationStatus;
import it.buonacaccia.model.NotificationTemplate;
import it.buonacaccia.model.User;
import it.buonacaccia.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import tools.jackson.databind.JsonNode;

/** US1 della 004: conferma di iscrizione al partecipante e avviso al presentatore (FR-008). */
class EnrollmentConfirmationMailTest extends EnrollmentTestSupport {

    @Autowired
    NotificationRepository notificationRepository;

    @Test
    void confermaNeiPostiConAdempimentiEScadenza() {
        String eventId = publishOpen();
        String number = enrolled("rover", eventId).get("number").asString();

        Mail mail = MAIL.expectOne("rover@buonacaccia.local", number);
        assertThat(mail.subject()).contains("registrata");
        assertThat(mail.text())
                .contains("Posizione: nei posti")
                .contains("autorizzazione come capo unità")
                .contains("pagamento della quota")
                .contains("Da completare entro il");
    }

    @Test
    void confermaInAttesaConPosizioneESenzaPagamento() {
        String eventId = publishOpen("seats", 1);
        enrolled("scolta", eventId);
        String number = enrolled("rover", eventId).get("number").asString();

        Mail mail = MAIL.expectOne("rover@buonacaccia.local", number);
        assertThat(mail.text())
                .contains("in attesa, numero 1 in lista")
                .contains("il pagamento si chiede solo allora")
                .doesNotContain("- pagamento della quota");
    }

    @Test
    void ilCapoCheIscriveRiceveUnAvviso() {
        String eventId = publish(scoutEvent()).get("id").asString();
        Result result = enrollFor("socio", "100006", eventId);
        assertThat(result.status()).as("%s", result.body()).isEqualTo(201);
        String number = result.body().get("payload").get("number").asString();

        Mail participant = MAIL.expectOne("esploratore@buonacaccia.local", number);
        assertThat(participant.subject()).contains("registrata");
        Mail presenter = MAIL.expectOne("socio@buonacaccia.local", number);
        assertThat(presenter.subject()).startsWith("Hai iscritto");
        assertThat(presenter.text()).contains("Posizione: nei posti");
    }

    @Test
    void senzaEmailNessunInvioMaIlRegistroLoSegna() {
        Query scolta = new Query(Criteria.where("username").is("scolta"));
        String email = mongoTemplate.findOne(scolta, User.class).getEmail();
        mongoTemplate.updateFirst(scolta, new Update().unset("email"), User.class);
        try {
            String eventId = publishOpen();
            JsonNode enrollment = enrolled("scolta", eventId);
            String id = enrollment.get("id").asString();

            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                List<Notification> log = notificationRepository.findByEnrollmentIdOrderByCreatedAtAsc(id);
                assertThat(log).anySatisfy(n -> {
                    assertThat(n.getTemplate()).isEqualTo(NotificationTemplate.ENROLLMENT_CONFIRMATION);
                    assertThat(n.getStatus()).isEqualTo(NotificationStatus.NO_ADDRESS);
                });
            });
            assertThat(MAIL.find(email, enrollment.get("number").asString())).isEmpty();
        } finally {
            mongoTemplate.updateFirst(scolta, new Update().set("email", email), User.class);
        }
    }
}
