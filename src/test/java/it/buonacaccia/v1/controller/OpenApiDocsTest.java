package it.buonacaccia.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;

import it.buonacaccia.AbstractTest;
import org.junit.jupiter.api.Test;

/** Documentazione OpenAPI generata (springdoc) e Swagger UI, pubbliche. */
class OpenApiDocsTest extends AbstractTest {

    @Test
    void apiDocsArePublicAndListTheEndpoints() {
        Result result = get("/api-docs");

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body().get("paths").has("/api/v1/users/me")).isTrue();
        assertThat(result.body().get("paths").has("/api/v1/mock/auth/findAll")).isTrue();
        // Feature 002: i 9 percorsi (14 operazioni) del contratto catalog.openapi.yaml.
        assertThat(result.body().get("paths").propertyNames()).contains(
                "/api/v1/regions",
                "/api/v1/event-types",
                "/api/v1/event-types/{code}",
                "/api/v1/event-types/{code}/deactivate",
                "/api/v1/events",
                "/api/v1/events/managed",
                "/api/v1/events/{id}",
                "/api/v1/events/{id}/publish",
                "/api/v1/events/{id}/close-registrations",
                "/api/v1/events/{id}/cancel",
                "/api/v1/events/{eventId}/enrollments",
                "/api/v1/enrollments/mine",
                "/api/v1/enrollments/presented",
                "/api/v1/enrollments/to-authorize",
                "/api/v1/enrollments/{id}",
                "/api/v1/enrollments/{id}/withdraw",
                "/api/v1/enrollments/{id}/obligations/{type}/approve",
                "/api/v1/enrollments/{id}/obligations/{type}/reject",
                "/api/v1/enrollments/{id}/obligations/{type}/satisfy");
        assertThat(result.body().get("components").get("securitySchemes").has("bearerAuth")).isTrue();
    }

    /** Il client del frontend si genera dai nomi delle operazioni: unici e leggibili, senza suffissi _1, _2 (feature 008). */
    @Test
    void operationIdsAreUniqueAndReadable() {
        java.util.List<String> ids = new java.util.ArrayList<>();
        get("/api-docs").body().get("paths").forEach(path ->
                path.forEach(operation -> ids.add(operation.get("operationId").asText())));

        assertThat(ids).isNotEmpty().doesNotHaveDuplicates();
        assertThat(ids).noneMatch(id -> id.matches(".*_\\d+$"));
        assertThat(ids).contains("event_publish", "enrollment_withdraw", "user_me");
    }

    /** Il client generato deve conoscere i valori delle azioni disponibili e la forma dei motivi di errore (feature 009). */
    @Test
    void contractListsActionValuesAndFieldErrors() {
        var schemas = get("/api-docs").body().get("components").get("schemas");

        var actions = schemas.get("EnrollmentDTO").get("properties").get("availableActions").get("items").get("enum");
        java.util.List<String> values = new java.util.ArrayList<>();
        actions.forEach(a -> values.add(a.asText()));
        assertThat(values).containsExactlyInAnyOrder("WITHDRAW", "SATISFY",
                "APPROVE_UNIT_LEADER", "REJECT_UNIT_LEADER", "APPROVE_GROUP_LEADER", "REJECT_GROUP_LEADER");

        var eventActions = schemas.get("EventDTO").get("properties").get("availableActions").get("items").get("enum");
        java.util.List<String> eventValues = new java.util.ArrayList<>();
        eventActions.forEach(a -> eventValues.add(a.asText()));
        assertThat(eventValues).containsExactlyInAnyOrder("EDIT", "PUBLISH", "CLOSE_REGISTRATIONS", "CANCEL");

        var fieldError = schemas.get("FieldError").get("properties");
        assertThat(fieldError.has("field")).isTrue();
        assertThat(fieldError.has("message")).isTrue();
    }

    /** Documenti e CSV si scaricano come file: il client generato deve ricevere un Blob, non un testo JSON (feature 011). */
    @Test
    void fileDownloadsAreDeclaredAsBinary() {
        var paths = get("/api-docs").body().get("paths");

        var document = paths.get("/api/v1/documents/{id}/content").get("get").get("responses").get("200").get("content");
        assertThat(document.has("application/octet-stream")).isTrue();
        assertThat(document.get("application/octet-stream").get("schema").get("format").asText()).isEqualTo("binary");
        assertThat(document.has("*/*")).isFalse();

        var csv = paths.get("/api/v1/events/{eventId}/reconciliation/export").get("get").get("responses").get("200").get("content");
        assertThat(csv.has("text/csv")).isTrue();
        assertThat(csv.get("text/csv").get("schema").get("format").asText()).isEqualTo("binary");
        assertThat(csv.has("*/*")).isFalse();
    }

    @Test
    void swaggerUiIsPublic() {
        assertThat(get("/swagger-ui/index.html").status()).isEqualTo(200);
    }
}
