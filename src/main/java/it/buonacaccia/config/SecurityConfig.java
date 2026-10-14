package it.buonacaccia.config;

import java.io.IOException;

import it.buonacaccia.v1.dto.ApiResponse;
import it.buonacaccia.config.error.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

/**
 * Resource server JWT (FR-007): tutto richiede un token valido tranne le risorse pubbliche
 * (mock, health, documentazione) e le letture del catalogo (feature 002: regioni, tipi evento,
 * ricerca e dettaglio degli eventi). Un token presente ma non valido dà 401 anche lì (research R9). Gli errori del filtro escono con l'envelope (FR-013, FR-015).
 * Ruoli ed entitlement si controllano con la guardia {@code @SecurityAccess}.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/api/v1/mock/**",
            // Link di autorizzazione dei capi (feature 004): la credenziale è il token nel percorso.
            "/api/v1/authorization-links/**",
            "/actuator/health",
            "/actuator/info",
            "/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/error"
    };

    /** Letture pubbliche del catalogo (FR-019, FR-020, FR-022). */
    private static final String[] PUBLIC_GET_PATHS = {
            "/api/v1/regions",
            "/api/v1/event-types",
            "/api/v1/event-types/*",
            "/api/v1/events",
            "/api/v1/events/*"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper jsonMapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/managed").authenticated()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint((request, response, e) ->
                                write(response, jsonMapper, ErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler((request, response, e) ->
                                write(response, jsonMapper, ErrorCode.FORBIDDEN)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, e) ->
                                write(response, jsonMapper, ErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler((request, response, e) ->
                                write(response, jsonMapper, ErrorCode.FORBIDDEN)));
        return http.build();
    }

    private static void write(HttpServletResponse response, JsonMapper jsonMapper, ErrorCode error) throws IOException {
        response.setStatus(error.httpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(jsonMapper.writeValueAsString(ApiResponse.error(error)));
    }
}
