package it.buonacaccia.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;

import it.buonacaccia.AbstractTest;
import org.junit.jupiter.api.Test;

/** Documentazione OpenAPI generata (springdoc) e Swagger UI, pubbliche. */
class OpenApiDocsTest extends AbstractTest {

    @Test
    void apiDocsArePublicAndListTheEndpoints() {
        Result result = get("/v3/api-docs");

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body().get("paths").has("/api/v1/users/me")).isTrue();
        assertThat(result.body().get("paths").has("/api/v1/mock/auth/findAll")).isTrue();
        assertThat(result.body().get("components").get("securitySchemes").has("bearerAuth")).isTrue();
    }

    @Test
    void swaggerUiIsPublic() {
        assertThat(get("/swagger-ui/index.html").status()).isEqualTo(200);
    }
}
