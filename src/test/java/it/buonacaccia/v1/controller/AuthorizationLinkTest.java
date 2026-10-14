package it.buonacaccia.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import it.buonacaccia.MailpitClient.Mail;
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

/** US2 della 004: il capo autorizza o nega dal link nell'e-mail, senza login (FR-015…FR-019). */
class AuthorizationLinkTest extends EnrollmentTestSupport {

    private static final String LINKS = "/api/v1/authorization-links/";
    private static final Pattern TOKEN = Pattern.compile("/autorizza/([A-Za-z0-9_-]{43})");
    private static final String CAPOGRUPPO = "capogruppo@buonacaccia.local";

    @Autowired
    NotificationRepository notificationRepository;

    @Test
    void ilCapoUnitaRiceveIlLinkELaLetturaNonCambiaNulla() {
        JsonNode enrollment = enrolled("rover", publishOpen());
        String number = enrollment.get("number").asString();

        Mail mail = MAIL.expectOne(CAPOGRUPPO, number);
        assertThat(mail.subject()).startsWith("Autorizzazione richiesta per");
        Result link = get(LINKS + token(mail));

        assertThat(link.status()).as("%s", link.body()).isEqualTo(200);
        JsonNode payload = link.body().get("payload");
        assertThat(payload.get("enrollmentNumber").asString()).isEqualTo(number);
        assertThat(payload.get("obligationType").asString()).isEqualTo("UNIT_LEADER_AUTHORIZATION");
        assertThat(payload.get("participantName").asString()).isNotBlank();
        assertThat(payload.get("eventCode").asString()).isNotBlank();
        assertThat(obligation(enrollment("rover", enrollment.get("id").asString()), "UNIT_LEADER_AUTHORIZATION")
                .get("status").asString()).isEqualTo("TODO");
    }

    @Test
    void autorizzareDalLinkSoddisfaLAdempimentoEIlLinkNonValePiu() {
        JsonNode enrollment = enrolled("rover", publishOpen());
        String token = token(MAIL.expectOne(CAPOGRUPPO, enrollment.get("number").asString()));

        Result approved = post(LINKS + token + "/approve", null, null);

        assertThat(approved.status()).as("%s", approved.body()).isEqualTo(200);
        assertThat(approved.body().get("payload").get("decision").asString()).isEqualTo("APPROVED");
        assertThat(obligation(enrollment("rover", enrollment.get("id").asString()), "UNIT_LEADER_AUTHORIZATION")
                .get("status").asString()).isEqualTo("SATISFIED");
        assertNotValid(post(LINKS + token + "/approve", null, null));
        assertNotValid(get(LINKS + token));
    }

    @Test
    void negareRichiedeIlMotivoERifiutaLIscrizione() {
        JsonNode enrollment = enrolled("rover", publishOpen());
        String token = token(MAIL.expectOne(CAPOGRUPPO, enrollment.get("number").asString()));

        Result withoutReason = post(LINKS + token + "/reject", null, Map.of("reason", " "));
        assertThat(withoutReason.status()).isEqualTo(422);

        Result rejected = post(LINKS + token + "/reject", null, Map.of("reason", "Non è pronto per la route"));
        assertThat(rejected.status()).as("%s", rejected.body()).isEqualTo(200);
        assertThat(rejected.body().get("payload").get("decision").asString()).isEqualTo("REJECTED");
        assertThat(rejected.body().get("payload").get("enrollmentStatus").asString()).isEqualTo("REJECTED");
        assertThat(enrollment("rover", enrollment.get("id").asString()).get("status").asString()).isEqualTo("REJECTED");
    }

    @Test
    void unTokenInventatoOAlteratoNonVale() {
        JsonNode enrollment = enrolled("rover", publishOpen());
        String token = token(MAIL.expectOne(CAPOGRUPPO, enrollment.get("number").asString()));
        String altered = (token.charAt(0) == 'A' ? 'B' : 'A') + token.substring(1);

        assertNotValid(get(LINKS + altered));
        assertNotValid(post(LINKS + altered + "/approve", null, null));
        assertNotValid(get(LINKS + "non-un-token"));
    }

    @Test
    void autorizzazioneDataDalSitoRendeIlLinkNonValido() {
        JsonNode enrollment = enrolled("rover", publishOpen());
        String token = token(MAIL.expectOne(CAPOGRUPPO, enrollment.get("number").asString()));

        Result fromSite = post(ENROLLMENTS + "/" + enrollment.get("id").asString()
                + "/obligations/UNIT_LEADER_AUTHORIZATION/approve", tokenOf("capogruppo"), null);
        assertThat(fromSite.status()).as("%s", fromSite.body()).isEqualTo(200);

        assertNotValid(get(LINKS + token));
    }

    @Test
    void conDueCapiQuandoUnoAutorizzaIlLinkDellAltroNonValePiu() {
        Query socio = new Query(Criteria.where("username").is("socio"));
        String unit = mongoTemplate.findOne(socio, User.class).getCensus().getUnitId();
        mongoTemplate.updateFirst(socio, new Update().set("census.unitId", "PIE-TO-01-RS"), User.class);
        try {
            JsonNode enrollment = enrolled("rover", publishOpen());
            String number = enrollment.get("number").asString();
            String first = token(MAIL.expectOne(CAPOGRUPPO, number));
            String second = token(MAIL.expectOne("socio@buonacaccia.local", number));
            assertThat(first).isNotEqualTo(second);

            assertThat(post(LINKS + first + "/approve", null, null).status()).isEqualTo(200);

            assertNotValid(get(LINKS + second));
        } finally {
            mongoTemplate.updateFirst(socio, new Update().set("census.unitId", unit), User.class);
        }
    }

    @Test
    void ilCapoGruppoCheSiIscriveNonRiceveIlLinkPerSeStesso() {
        JsonNode enrollment = enrolled("capogruppo", publish(leaderEvent()).get("id").asString());
        String id = enrollment.get("id").asString();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(notificationRepository.findByEnrollmentIdOrderByCreatedAtAsc(id)).anySatisfy(n -> {
                    assertThat(n.getTemplate()).isEqualTo(NotificationTemplate.AUTHORIZATION_REQUEST);
                    assertThat(n.getStatus()).isEqualTo(NotificationStatus.NO_RECIPIENT);
                }));
        assertThat(MAIL.find(CAPOGRUPPO, enrollment.get("number").asString()))
                .noneMatch(m -> m.subject().startsWith("Autorizzazione richiesta"));
    }

    @Test
    void ilCapoGruppoRiceveIlLinkPerUnCapoDelSuoGruppo() {
        JsonNode enrollment = enrolled("socio", publish(leaderEvent()).get("id").asString());

        Mail mail = MAIL.expectOne(CAPOGRUPPO, enrollment.get("number").asString());
        Result link = get(LINKS + token(mail));
        assertThat(link.status()).isEqualTo(200);
        assertThat(link.body().get("payload").get("obligationType").asString()).isEqualTo("GROUP_LEADER_AUTHORIZATION");
    }

    private static String token(Mail mail) {
        Matcher matcher = TOKEN.matcher(mail.text());
        assertThat(matcher.find()).as("link nel testo: %s", mail.text()).isTrue();
        return matcher.group(1);
    }

    private static void assertNotValid(Result result) {
        assertThat(result.status()).as("%s", result.body()).isEqualTo(404);
        assertThat(result.body().get("errorCode").asInt()).isEqualTo(40420);
        assertThat(result.body().get("errorDomain").asString()).isEqualTo("NOTIFICATION");
    }
}
