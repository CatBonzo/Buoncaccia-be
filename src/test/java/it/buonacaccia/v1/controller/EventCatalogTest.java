package it.buonacaccia.v1.controller;

import static it.buonacaccia.v1.controller.EventFixtures.fields;
import static it.buonacaccia.v1.controller.EventFixtures.validEvent;
import static it.buonacaccia.v1.controller.EventFixtures.with;
import static it.buonacaccia.v1.controller.EventManagementTest.assertError;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import it.buonacaccia.AbstractTest;
import it.buonacaccia.model.EnrollmentMode;
import it.buonacaccia.model.Event;
import it.buonacaccia.model.EventLevel;
import it.buonacaccia.model.EventLifecycle;
import it.buonacaccia.model.Region;
import it.buonacaccia.service.EventStatusResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** US2: ricerca e dettaglio pubblici (FR-019, FR-020); le bozze restano invisibili (SC-003). */
class EventCatalogTest extends AbstractTest {

    private static final String EVENTS = "/api/v1/events";

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private EventStatusResolver statusResolver;

    @Test
    void searchIsPublicAndHidesDrafts() {
        String tag = tag();
        JsonNode published = publish(create("segreteria", titled(validEvent("PIE", "2032"), tag, "A")));
        JsonNode draft = create("segreteria", titled(validEvent("PIE", "2032"), tag, "B"));

        JsonNode page = search("q=" + tag);

        assertThat(page.get("totalElements").asLong()).isEqualTo(1);
        assertThat(ids(page)).containsExactly(published.get("id").asString()).doesNotContain(draft.get("id").asString());
        assertThat(page.get("page").asInt()).isZero();
        assertThat(page.get("size").asInt()).isEqualTo(20);
        assertThat(page.get("totalPages").asInt()).isEqualTo(1);
    }

    @Test
    void filtersByRegionTypeCategoryAndDates() {
        String tag = tag();
        JsonNode pie = publish(create("segreteria", titled(validEvent("PIE", "2032"), tag, "Piemonte")));
        JsonNode lig = publish(create("segreteria-lig", titled(with(validEvent("LIG", "2033"), "typeCode", "CFM_EG"), tag, "Liguria")));

        assertThat(ids(search("q=" + tag + "&region=LIG"))).containsExactly(lig.get("id").asString());
        assertThat(ids(search("q=" + tag + "&type=ROSS"))).containsExactly(pie.get("id").asString());
        assertThat(ids(search("q=" + tag + "&category=LEADER_TRAINING"))).containsExactly(lig.get("id").asString());
        assertThat(ids(search("q=" + tag + "&startFrom=2033-01-01"))).containsExactly(lig.get("id").asString());
        assertThat(ids(search("q=" + tag + "&startTo=2032-12-31"))).containsExactly(pie.get("id").asString());
        assertThat(ids(search("q=" + tag + "&availability=NOT_YET_OPEN"))).hasSize(2);
        assertThat(ids(search("q=" + tag + "&status=REGISTRATION_OPEN"))).isEmpty();
    }

    @Test
    void textSearchIsCaseInsensitiveAndLiteral() {
        String tag = tag();
        JsonNode event = publish(create("segreteria", titled(validEvent("PIE", "2032"), tag, "Campo (E/G) [speciale]")));

        assertThat(ids(search("q=" + encode(tag.toLowerCase() + " campo (e/g) [speciale]"))))
                .containsExactly(event.get("id").asString());
        assertThat(ids(search("q=" + encode(tag + ".*")))).isEmpty();
    }

    @Test
    void sortsAndPages() {
        String tag = tag();
        publish(create("segreteria", titled(with(validEvent("PIE", "2032"), "startDate", "2032-07-22"), tag, "Bravo")));
        publish(create("segreteria", titled(with(validEvent("PIE", "2032"), "startDate", "2032-07-21"), tag, "Charlie")));
        publish(create("segreteria", titled(with(validEvent("PIE", "2032"), "startDate", "2032-07-23"), tag, "Alfa")));

        assertThat(titles(search("q=" + tag + "&sort=title,asc"))).containsExactly(tag + " Alfa", tag + " Bravo", tag + " Charlie");
        assertThat(titles(search("q=" + tag + "&sort=startDate,desc"))).containsExactly(tag + " Alfa", tag + " Bravo", tag + " Charlie");
        assertThat(titles(search("q=" + tag + "&sort=startDate,asc"))).containsExactly(tag + " Charlie", tag + " Bravo", tag + " Alfa");

        JsonNode second = search("q=" + tag + "&sort=title,asc&size=2&page=1");
        assertThat(titles(second)).containsExactly(tag + " Charlie");
        assertThat(second.get("totalElements").asLong()).isEqualTo(3);
        assertThat(second.get("totalPages").asInt()).isEqualTo(2);

        JsonNode beyond = search("q=" + tag + "&page=9");
        assertThat(beyond.get("content").size()).isZero();
        assertThat(beyond.get("totalElements").asLong()).isEqualTo(3);
    }

    @Test
    void noResultIsAnEmptyPage() {
        JsonNode page = search("q=" + tag());

        assertThat(page.get("content").size()).isZero();
        assertThat(page.get("totalElements").asLong()).isZero();
    }

    @Test
    void invalidParametersAreNamed() {
        assertInvalid("size=101", "size");
        assertInvalid("page=-1", "page");
        assertInvalid("sort=altro,asc", "sort");
        assertInvalid("startFrom=2032-13-01", "startFrom");
        assertInvalid("status=DRAFT", "status");
    }

    @Test
    void cancelledOnlyWithExplicitStatus() {
        String tag = tag();
        JsonNode event = publish(create("segreteria", titled(validEvent("PIE", "2032"), tag, "Annullato")));
        post(EVENTS + "/" + event.get("id").asString() + "/cancel", tokenOf("segreteria"),
                Map.of("reason", "Neve", "version", event.get("version").asLong()));

        assertThat(ids(search("q=" + tag))).isEmpty();
        assertThat(ids(search("q=" + tag + "&status=CANCELLED"))).containsExactly(event.get("id").asString());
    }

    @Test
    void detailIsPublicButDraftsOnlyForManagers() {
        JsonNode published = publish(create("segreteria", validEvent("PIE", "2032")));
        JsonNode draft = create("segreteria", validEvent("PIE", "2032"));
        String draftPath = EVENTS + "/" + draft.get("id").asString();

        Result detail = get(EVENTS + "/" + published.get("id").asString());
        assertThat(detail.status()).isEqualTo(200);
        assertThat(detail.body().get("payload").get("seats").asInt()).isEqualTo(40);
        assertThat(detail.body().get("payload").get("availability").asString()).isEqualTo("NOT_YET_OPEN");
        assertThat(detail.body().get("payload").has("registrationOpensAt")).isFalse();

        assertError(get(draftPath), 404, 40400);
        assertError(get(draftPath, tokenOf("socio")), 404, 40400);
        assertError(get(draftPath, tokenOf("segreteria-lig")), 404, 40400);
        assertThat(get(draftPath, tokenOf("segreteria")).status()).isEqualTo(200);
        assertThat(get(draftPath, tokenOf("amministratore")).status()).isEqualTo(200);
        assertError(get(EVENTS + "/" + UUID.randomUUID()), 404, 40400);
        assertError(get(draftPath, "non-un-jwt"), 401, 40100);
    }

    @Test
    void firstPageOfAThousandEventsIsFast() {
        String tag = tag();
        Instant now = Instant.now();
        List<Event> events = IntStream.range(0, 1000).mapToObj(i -> {
            Event event = Event.builder()
                    .id(UUID.randomUUID().toString()).code(tag + "-" + i).typeCode(i % 2 == 0 ? "ROSS" : "CFA")
                    .title(tag + " Evento " + i).level(EventLevel.REGIONAL).region(Region.SAR).location("Cagliari")
                    .registrationOpenDate(LocalDate.of(2035, 3, 1)).registrationCloseDate(LocalDate.of(2035, 6, 30))
                    .startDate(LocalDate.of(2035, 7, 1).plusDays(i % 60)).endDate(LocalDate.of(2035, 9, 30))
                    .seats(30).fee(BigDecimal.TEN).enrollmentMode(EnrollmentMode.SELF)
                    .lifecycle(EventLifecycle.PUBLISHED).createdAt(now).updatedAt(now)
                    .build();
            statusResolver.computeInstants(event);
            return event;
        }).toList();
        mongoTemplate.insertAll(events);
        get(EVENTS + "?region=SAR&size=1");

        long start = System.nanoTime();
        JsonNode page = search("region=SAR&category=RS&availability=NOT_YET_OPEN&startFrom=2035-07-10&sort=startDate,asc&size=20");
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(page.get("content").size()).isEqualTo(20);
        assertThat(millis).as("SC-004: prima pagina su 1.000 eventi").isLessThan(1000);
    }

    // ---------- aiuti ----------

    private JsonNode search(String query) {
        Result result = get(EVENTS + "?" + query);
        assertThat(result.status()).as("ricerca %s: %s", query, result.body()).isEqualTo(200);
        return result.body().get("payload");
    }

    private void assertInvalid(String query, String field) {
        Result result = get(EVENTS + "?" + query);
        assertError(result, 422, 42200);
        assertThat(fields(result.body().get("payload"))).as(query).contains(field);
    }

    private JsonNode create(String username, Map<String, Object> body) {
        Result result = post(EVENTS, tokenOf(username), body);
        assertThat(result.status()).as("creazione: %s", result.body()).isEqualTo(201);
        return result.body().get("payload");
    }

    private JsonNode publish(JsonNode draft) {
        String region = draft.get("region").asString();
        String token = tokenOf("LIG".equals(region) ? "segreteria-lig" : "segreteria");
        Result result = post(EVENTS + "/" + draft.get("id").asString() + "/publish", token, null);
        assertThat(result.status()).as("pubblicazione: %s", result.body()).isEqualTo(200);
        return result.body().get("payload");
    }

    private static Map<String, Object> titled(Map<String, Object> body, String tag, String title) {
        return with(body, "title", tag + " " + title);
    }

    /** Parola unica per isolare i dati di un test dagli altri nello stesso database. */
    private static String tag() {
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    private static List<String> ids(JsonNode page) {
        return page.get("content").valueStream().map(e -> e.get("id").asString()).toList();
    }

    private static List<String> titles(JsonNode page) {
        return page.get("content").valueStream().map(e -> e.get("title").asString()).toList();
    }
}
