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

    @Test
    void swaggerUiIsPublic() {
        assertThat(get("/swagger-ui/index.html").status()).isEqualTo(200);
    }
}
