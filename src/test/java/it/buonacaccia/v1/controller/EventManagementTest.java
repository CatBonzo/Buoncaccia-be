package it.buonacaccia.v1.controller;

import static it.buonacaccia.v1.controller.EventFixtures.fields;
import static it.buonacaccia.v1.controller.EventFixtures.validEvent;
import static it.buonacaccia.v1.controller.EventFixtures.version;
import static it.buonacaccia.v1.controller.EventFixtures.with;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Map;

import it.buonacaccia.AbstractTest;
import it.buonacaccia.config.ClockConfig;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** US1 (creare e pubblicare nella propria regione) e US5 (modificare e annullare). */
class EventManagementTest extends AbstractTest {

    private static final String EVENTS = "/api/v1/events";

    // ---------- US1: creazione ----------

    @Test
    void segreteriaCreatesDraftWithTypeDefaults() {
        Result result = post(EVENTS, tokenOf("segreteria"), with(validEvent("PIE", "2030"), "requirements", null));

        assertThat(result.status()).isEqualTo(201);
        JsonNode event = result.body().get("payload");
        assertThat(event.get("status").asString()).isEqualTo("DRAFT");
        assertThat(event.get("code").asString()).matches("PIE-2030-\\d{3}");
        assertThat(event.get("typeName").asString()).isNotBlank();
        assertThat(event.get("category").asString()).isEqualTo("RS");
        assertThat(event.get("enrolledCount").asInt()).isZero();
        assertThat(event.get("version").asLong()).isZero();
        // FR-005: requisiti e adempimenti dal tipo evento ROSS del mock.
        assertThat(event.get("requirements").get("minAge").asInt()).isEqualTo(16);
        assertThat(event.get("obligations").size()).isPositive();
    }

    @Test
    void codesAreConsecutiveForRegionAndYear() {
        String token = tokenOf("segreteria");
        String first = post(EVENTS, token, validEvent("PIE", "2031")).body().get("payload").get("code").asString();
        String second = post(EVENTS, token, validEvent("PIE", "2031")).body().get("payload").get("code").asString();

        int n = Integer.parseInt(first.substring(first.lastIndexOf('-') + 1));
        assertThat(second).isEqualTo("PIE-2031-%03d".formatted(n + 1));
    }

    @Test
    void segreteriaWithoutRegionGetsOwnRegion() {
        Result result = post(EVENTS, tokenOf("segreteria"), with(validEvent("PIE", "2030"), "region", null));

        assertThat(result.status()).isEqualTo(201);
        assertThat(result.body().get("payload").get("region").asString()).isEqualTo("PIE");
    }

    @Test
    void incompleteDraftIsSaved() {
        Result result = post(EVENTS, tokenOf("segreteria"), Map.of("typeCode", "ROSS", "level", "REGIONAL"));

        assertThat(result.status()).isEqualTo(201);
        assertThat(result.body().get("payload").get("status").asString()).isEqualTo("DRAFT");
    }

    @Test
    void adminCreatesNationalEvent() {
        Result result = post(EVENTS, tokenOf("amministratore"),
                with(validEvent(null, "2030"), "level", "NATIONAL", "typeCode", "CFA"));

        assertThat(result.status()).isEqualTo(201);
        assertThat(result.body().get("payload").get("code").asString()).startsWith("NAZ-2030-");
    }

    @Test
    void segreteriaCannotCreateForOtherRegionOrNational() {
        String token = tokenOf("segreteria");

        assertError(post(EVENTS, token, validEvent("LIG", "2030")), 403, 40300);
        assertError(post(EVENTS, token, with(validEvent(null, "2030"), "level", "NATIONAL")), 403, 40300);
    }

    @Test
    void creationNeedsTokenAndEntitlement() {
        assertError(post(EVENTS, null, validEvent("PIE", "2030")), 401, 40100);
        assertError(post(EVENTS, tokenOf("socio"), validEvent("PIE", "2030")), 403, 40300);
    }

    @Test
    void invalidBodyListsFields() {
        Result result = post(EVENTS, tokenOf("segreteria"),
                with(validEvent("PIE", "2030"), "typeCode", null, "province", "Torino", "seats", 0));

        assertError(result, 422, 42200);
        assertThat(fields(result.body().get("payload"))).contains("typeCode", "province", "seats");
    }

    @Test
    void unknownOrInactiveTypeIsRejected() {
        Result unknown = post(EVENTS, tokenOf("segreteria"), with(validEvent("PIE", "2030"), "typeCode", "NON_ESISTE"));

        assertError(unknown, 422, 42200);
        assertThat(fields(unknown.body().get("payload"))).containsExactly("typeCode");
    }

    // ---------- US1: pubblicazione ----------

    @Test
    void publishedBeforeOpeningIsPublished() {
        JsonNode draft = create("segreteria", validEvent("PIE", "2030"));

        Result result = post(EVENTS + "/" + draft.get("id").asString() + "/publish", tokenOf("segreteria"), version(0));

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body().get("payload").get("status").asString()).isEqualTo("PUBLISHED");
        assertThat(result.body().get("payload").get("availability").asString()).isEqualTo("NOT_YET_OPEN");
    }

    @Test
    void publishedAfterOpeningIsOpenAtOnce() {
        clock.setInstant(LocalDateTime.parse("2030-03-05T10:00").atZone(ClockConfig.ROME).toInstant());
        JsonNode draft = create("segreteria", validEvent("PIE", "2030"));

        Result result = post(EVENTS + "/" + draft.get("id").asString() + "/publish", tokenOf("segreteria"), null);

        assertThat(result.body().get("payload").get("status").asString()).isEqualTo("REGISTRATION_OPEN");
        assertThat(result.body().get("payload").get("availability").asString()).isEqualTo("OPEN");
    }

    @Test
    void publishListsEveryBrokenInvariant() {
        JsonNode draft = create("segreteria", with(validEvent("PIE", "2030"),
                "title", null, "endDate", "2030-07-01", "registrationCloseDate", "2030-08-01"));

        Result result = post(EVENTS + "/" + draft.get("id").asString() + "/publish", tokenOf("segreteria"), null);

        assertError(result, 422, 42201);
        assertThat(result.body().get("errorDomain").asString()).isEqualTo("CATALOG");
        assertThat(fields(result.body().get("payload")))
                .containsExactlyInAnyOrder("title", "endDate", "registrationCloseDate");
    }

    @Test
    void zoneIsRequiredOnlyForZoneLevel() {
        JsonNode zoneWithout = create("segreteria", with(validEvent("PIE", "2030"), "level", "ZONE"));
        JsonNode regionalWith = create("segreteria", with(validEvent("PIE", "2030"), "zone", "Torino Nord"));

        Result first = post(EVENTS + "/" + zoneWithout.get("id").asString() + "/publish", tokenOf("segreteria"), null);
        Result second = post(EVENTS + "/" + regionalWith.get("id").asString() + "/publish", tokenOf("segreteria"), null);

        assertThat(fields(first.body().get("payload"))).containsExactly("zone");
        assertThat(fields(second.body().get("payload"))).containsExactly("zone");
    }

    @Test
    void otherRegionCannotPublish() {
        JsonNode draft = create("segreteria", validEvent("PIE", "2030"));

        assertError(post(EVENTS + "/" + draft.get("id").asString() + "/publish", tokenOf("segreteria-lig"), null), 403, 40300);
    }

    @Test
    void publishingTwiceIsAConflict() {
        JsonNode draft = create("segreteria", validEvent("PIE", "2030"));
        String path = EVENTS + "/" + draft.get("id").asString() + "/publish";
        post(path, tokenOf("segreteria"), null);

        assertError(post(path, tokenOf("segreteria"), null), 409, 40901);
    }

    @Test
    void draftStaysPublishableAfterTypeIsDeactivated() {
        String admin = tokenOf("amministratore");
        String code = "TMP_" + (System.nanoTime() % 100000);
        post("/api/v1/event-types", admin, Map.of("code", code, "name", "Tipo temporaneo", "category", "EG"));
        JsonNode draft = create("segreteria", with(validEvent("PIE", "2030"), "typeCode", code));
        post("/api/v1/event-types/" + code + "/deactivate", admin, null);

        Result result = post(EVENTS + "/" + draft.get("id").asString() + "/publish", tokenOf("segreteria"), null);

        assertThat(result.status()).isEqualTo(200);
        assertError(post(EVENTS, tokenOf("segreteria"), with(validEvent("PIE", "2030"), "typeCode", code)), 422, 42200);
    }

    // ---------- US1: i miei eventi ----------

    @Test
    void managedListShowsOwnRegionIncludingDrafts() {
        JsonNode pie = create("segreteria", validEvent("PIE", "2030"));
        JsonNode lig = create("segreteria-lig", validEvent("LIG", "2030"));

        JsonNode mine = get(EVENTS + "/managed?status=DRAFT&size=100", tokenOf("segreteria")).body().get("payload");
        JsonNode all = get(EVENTS + "/managed?status=DRAFT&size=100", tokenOf("amministratore")).body().get("payload");

        assertThat(ids(mine)).contains(pie.get("id").asString()).doesNotContain(lig.get("id").asString());
        assertThat(mine.get("content").valueStream().map(e -> e.get("region").asString())).containsOnly("PIE");
        assertThat(all.get("totalElements").asLong()).isGreaterThanOrEqualTo(2);
        assertError(get(EVENTS + "/managed", tokenOf("socio")), 403, 40300);
        assertError(get(EVENTS + "/managed"), 401, 40100);
    }

    // ---------- US5: modifica ----------

    @Test
    void draftIsFullyEditable() {
        JsonNode draft = create("segreteria", validEvent("PIE", "2030"));

        Result result = put(EVENTS + "/" + draft.get("id").asString(), tokenOf("segreteria"),
                with(validEvent("PIE", "2030"), "typeCode", "CFM_EG", "level", "ZONE", "zone", "Torino", "version", 0));

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body().get("payload").get("typeCode").asString()).isEqualTo("CFM_EG");
        assertThat(result.body().get("payload").get("version").asLong()).isEqualTo(1);
    }

    @Test
    void publishedEventAllowsOnlyEditableFields() {
        Map<String, Object> body = validEvent("PIE", "2030");
        JsonNode event = publish(create("segreteria", body));
        String path = EVENTS + "/" + event.get("id").asString();
        long v = event.get("version").asLong();

        Result ok = put(path, tokenOf("segreteria"), with(body, "title", "Nuovo titolo", "seats", 50, "version", v));
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.body().get("payload").get("title").asString()).isEqualTo("Nuovo titolo");

        long v2 = ok.body().get("payload").get("version").asLong();
        Result locked = put(path, tokenOf("segreteria"), with(body, "typeCode", "CFA", "version", v2));
        assertError(locked, 409, 40904);
        assertThat(locked.body().get("errorMessage").asString()).contains("typeCode");
        assertError(put(path, tokenOf("segreteria"), with(body, "registrationOpenDate", "2030-02-01", "version", v2)), 409, 40904);
        assertError(put(path, tokenOf("segreteria"), with(body, "endDate", "2030-07-01", "version", v2)), 422, 42201);
    }

    @Test
    void staleVersionIsRejected() {
        Map<String, Object> body = validEvent("PIE", "2030");
        JsonNode draft = create("segreteria", body);
        String path = EVENTS + "/" + draft.get("id").asString();

        assertThat(put(path, tokenOf("segreteria"), with(body, "title", "Prima", "version", 0)).status()).isEqualTo(200);
        assertError(put(path, tokenOf("segreteria"), with(body, "title", "Seconda", "version", 0)), 409, 40903);
        assertError(put(path, tokenOf("segreteria"), with(body, "title", "Senza versione")), 422, 42200);
    }

    @Test
    void finishedEventIsNotEditable() {
        Map<String, Object> body = validEvent("PIE", "2030");
        JsonNode event = publish(create("segreteria", body));
        clock.setInstant(LocalDateTime.parse("2030-07-21T10:00").atZone(ClockConfig.ROME).toInstant());

        Result result = put(EVENTS + "/" + event.get("id").asString(), tokenOf("segreteria"),
                with(body, "title", "Troppo tardi", "version", event.get("version").asLong()));

        assertError(result, 409, 40901);
    }

    @Test
    void otherRegionCannotEdit() {
        Map<String, Object> body = validEvent("PIE", "2030");
        JsonNode draft = create("segreteria", body);

        assertError(put(EVENTS + "/" + draft.get("id").asString(), tokenOf("segreteria-lig"),
                with(body, "version", 0)), 403, 40300);
    }

    // ---------- US5: annullamento ----------

    @Test
    void cancelNeedsReasonAndKeepsEventVisible() {
        JsonNode event = publish(create("segreteria", validEvent("PIE", "2030")));
        String path = EVENTS + "/" + event.get("id").asString();
        long v = event.get("version").asLong();

        assertError(post(path + "/cancel", tokenOf("segreteria"), Map.of("version", v)), 422, 42200);
        assertError(post(path + "/cancel", tokenOf("segreteria"), Map.of("reason", "x".repeat(501), "version", v)), 422, 42200);
        assertError(post(path + "/cancel", tokenOf("segreteria-lig"), Map.of("reason", "Maltempo", "version", v)), 403, 40300);

        Result cancelled = post(path + "/cancel", tokenOf("segreteria"), Map.of("reason", "Maltempo", "version", v));
        assertThat(cancelled.status()).isEqualTo(200);

        JsonNode detail = get(path).body().get("payload");
        assertThat(detail.get("status").asString()).isEqualTo("CANCELLED");
        assertThat(detail.get("cancellationReason").asString()).isEqualTo("Maltempo");
        assertThat(detail.get("availability").asString()).isEqualTo("CLOSED");
        assertError(post(path + "/cancel", tokenOf("segreteria"),
                Map.of("reason", "Di nuovo", "version", detail.get("version").asLong())), 409, 40901);
    }

    @Test
    void completedEventCannotBeCancelled() {
        JsonNode event = publish(create("segreteria", validEvent("PIE", "2030")));
        clock.setInstant(LocalDateTime.parse("2030-08-01T10:00").atZone(ClockConfig.ROME).toInstant());

        assertError(post(EVENTS + "/" + event.get("id").asString() + "/cancel", tokenOf("segreteria"),
                Map.of("reason", "Troppo tardi", "version", event.get("version").asLong())), 409, 40901);
    }

    // ---------- aiuti ----------

    private JsonNode create(String username, Map<String, Object> body) {
        Result result = post(EVENTS, tokenOf(username), body);
        assertThat(result.status()).as("creazione: %s", result.body()).isEqualTo(201);
        return result.body().get("payload");
    }

    private JsonNode publish(JsonNode draft) {
        Result result = post(EVENTS + "/" + draft.get("id").asString() + "/publish", tokenOf("segreteria"), null);
        assertThat(result.status()).as("pubblicazione: %s", result.body()).isEqualTo(200);
        return result.body().get("payload");
    }

    private static java.util.List<String> ids(JsonNode page) {
        return page.get("content").valueStream().map(e -> e.get("id").asString()).toList();
    }

    static void assertError(Result result, int status, int errorCode) {
        assertThat(result.status()).as("corpo: %s", result.body()).isEqualTo(status);
        assertThat(result.body().get("errorCode").asInt()).isEqualTo(errorCode);
        assertThat(result.body().has("errorMessage")).isTrue();
        assertThat(result.body().has("errorDomain")).isTrue();
        assertThat(result.body().has("payload")).isTrue();
    }
}
