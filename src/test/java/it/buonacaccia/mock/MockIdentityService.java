package it.buonacaccia.mock;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.util.UUID;

import it.buonacaccia.mock.MockedIdentityProperties.CensusData;
import it.buonacaccia.mock.MockedIdentityProperties.MockedUser;
import it.buonacaccia.model.Census;
import it.buonacaccia.model.User;
import it.buonacaccia.model.UserStatus;
import it.buonacaccia.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Utenti di prova con il loro token (profilo {@code mocked}, FR-001…FR-006), solo nel codice di test. I token sono solo
 * in memoria: non stanno nel file né nel database e valgono fino al riavvio.
 */
@Slf4j
@Service
@Profile("mocked")
@RequiredArgsConstructor
public class MockIdentityService {

    /** Voce del file con l'id Buona Caccia definitivo e il token emesso. */
    public record IssuedMockUser(MockedUser entry, String buonaCacciaUUID, String accessToken) {
    }

    private final UserService userService;
    private final JwtEncoder mockJwtEncoder;
    @Value("${buonacaccia.mock-auth.token-validity:P365D}")
    private final Duration tokenValidity;
    private volatile List<IssuedMockUser> issued = List.of();

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
                    User user = userService.upsertByAgesciUUID(toUser(entry));
                    log.info("Utente di prova {} ({}): token emesso", entry.username(), user.getId());
                    return new IssuedMockUser(entry, user.getId(), issueToken(entry, user.getId()));
                })
                .toList();
        return issued;
    }

    public List<IssuedMockUser> findAll() {
        return issued;
    }

    /** Voce di findAll: dati del file, id definitivo, emittente e token. */
    public static MockUserResponse toResponse(IssuedMockUser issued) {
        MockedUser entry = issued.entry();
        return new MockUserResponse(entry.agesciUUID(), issued.buonaCacciaUUID(), entry.label(), entry.username(),
                entry.firstName(), entry.lastName(), entry.email(), entry.authentication(), entry.census(),
                MockAuthConfig.ISSUER, issued.accessToken());
    }

    /** Utente candidato per l'upsert: id dalla voce del file se indicato, altrimenti un UUID nuovo. */
    private static User toUser(MockedUser entry) {
        Instant now = Instant.now();
        String id = entry.buonaCacciaUUID() != null ? entry.buonaCacciaUUID() : UUID.randomUUID().toString();
        return User.builder()
                .id(id)
                .agesciUUID(entry.agesciUUID())
                .username(entry.username())
                .email(entry.email())
                .firstName(entry.firstName())
                .lastName(entry.lastName())
                .status(UserStatus.ACTIVE)
                .census(toCensus(entry.census()))
                .roles(entry.authentication().role())
                .entitlements(entry.authentication().entitlement())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private static Census toCensus(CensusData data) {
        if (data == null) {
            return null;
        }
        return Census.builder()
                .memberCode(data.memberCode())
                .groupId(data.groupId())
                .unitId(data.unitId())
                .branch(data.branch())
                .memberType(data.memberType())
                .status(data.status())
                .year(data.year())
                .build();
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
