package it.buonacaccia.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import it.buonacaccia.config.MockAuthConfig;
import it.buonacaccia.config.MockedIdentityProperties.MockedUser;
import it.buonacaccia.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Utenti di prova con il loro token (profilo {@code mocked}, FR-001…FR-006). I token sono solo
 * in memoria: non stanno nel file né nel database e valgono fino al riavvio.
 */
@Service
@Profile("mocked")
public class MockIdentityService {

    /** Voce del file con l'id Buona Caccia definitivo e il token emesso. */
    public record IssuedMockUser(MockedUser entry, String buonaCacciaUUID, String accessToken) {
    }

    private final UserService userService;
    private final JwtEncoder mockJwtEncoder;
    private final Duration tokenValidity;
    private volatile List<IssuedMockUser> issued = List.of();

    public MockIdentityService(UserService userService, JwtEncoder mockJwtEncoder,
            @Value("${buonacaccia.mock-auth.token-validity:P365D}") Duration tokenValidity) {
        this.userService = userService;
        this.mockJwtEncoder = mockJwtEncoder;
        this.tokenValidity = tokenValidity;
    }

    /** Salva gli utenti di prova ed emette un token per ciascuno; due voci con lo stesso agesciUUID fermano l'avvio. */
    public List<IssuedMockUser> load(List<MockedUser> entries) {
        Set<String> seen = new HashSet<>();
        for (MockedUser entry : entries) {
            if (!seen.add(entry.agesciUUID())) {
                throw new IllegalStateException("mocked-identity.yml: agesciUUID duplicato " + entry.agesciUUID());
            }
        }
        issued = entries.stream()
                .map(entry -> {
                    User user = userService.upsertMockUser(entry);
                    return new IssuedMockUser(entry, user.id(), issueToken(entry, user.id()));
                })
                .toList();
        return issued;
    }

    public List<IssuedMockUser> findAll() {
        return issued;
    }

    private String issueToken(MockedUser entry, String buonaCacciaUUID) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(MockAuthConfig.ISSUER)
                .subject(buonaCacciaUUID)
                .issuedAt(now)
                .expiresAt(now.plus(tokenValidity))
                .claim("agesciUUID", entry.agesciUUID())
                .claim("isMemberOf", entry.authentication().isMemberOf())
                .claim("role", entry.authentication().role())
                .claim("entitlement", entry.authentication().entitlement())
                .claim("preferred_username", entry.username())
                .claim("given_name", entry.firstName())
                .claim("family_name", entry.lastName());
        if (entry.email() != null) {
            claims.claim("email", entry.email());
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return mockJwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
