package it.buonacaccia.api;

import java.util.List;
import java.util.Map;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.buonacaccia.api.dto.ApiResponse;
import it.buonacaccia.api.dto.MockUserResponse;
import it.buonacaccia.mapper.UserMapper;
import it.buonacaccia.service.MockIdentityService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Utenti di prova e chiave pubblica: esistono solo con il profilo {@code mocked} (FR-004, FR-005). */
@RestController
@Profile("mocked")
@RequestMapping("/api/v1/mock/auth")
@Tag(name = "mock-auth", description = "Utenti di prova e chiave pubblica (solo profilo mocked)")
public class MockAuthController {

    private final MockIdentityService mockIdentityService;
    private final UserMapper userMapper;
    private final RSAKey mockSigningKey;

    public MockAuthController(MockIdentityService mockIdentityService, UserMapper userMapper, RSAKey mockSigningKey) {
        this.mockIdentityService = mockIdentityService;
        this.userMapper = userMapper;
        this.mockSigningKey = mockSigningKey;
    }

    @GetMapping("/findAll")
    @SecurityRequirements
    @Operation(summary = "Utenti di prova con il loro access token",
            description = "I token cambiano a ogni riavvio del backend.")
    public ApiResponse<List<MockUserResponse>> findAll() {
        return ApiResponse.ok(mockIdentityService.findAll().stream().map(userMapper::toMockUser).toList());
    }

    @GetMapping("/jwks")
    @SecurityRequirements
    @Operation(summary = "Chiave pubblica dei token mock",
            description = "JWK Set standard, senza envelope: lo leggono le librerie di sicurezza.")
    public Map<String, Object> jwks() {
        return new JWKSet(mockSigningKey.toPublicJWK()).toJSONObject();
    }
}
