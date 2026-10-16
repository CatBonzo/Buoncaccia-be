package it.buonacaccia.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentazione generata da springdoc (research R11): Swagger UI su /swagger-ui.html.
 * Il pulsante "Authorize" accetta un accessToken preso da /api/v1/mock/auth/findAll.
 * Il contratto di riferimento resta quello in specs/NNN-.../contracts.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI buonaCacciaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Buona Caccia API")
                        .version("v1")
                        .description("Backend delle iscrizioni agli eventi AGESCI. Ogni risposta usa l'envelope "
                                + "errorCode / errorMessage / errorDomain / payload."))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    /**
     * Nome dell'operazione = "controller_metodo" (es. event_publish): unico e leggibile nel client TypeScript
     * generato, al posto dei suffissi _1, _2 di springdoc.
     */
    @Bean
    OperationCustomizer readableOperationIds() {
        return (operation, handlerMethod) -> {
            String controller = handlerMethod.getBeanType().getSimpleName().replaceFirst("Controller$", "");
            operation.setOperationId(Character.toLowerCase(controller.charAt(0)) + controller.substring(1)
                    + "_" + handlerMethod.getMethod().getName());
            return operation;
        };
    }
}
