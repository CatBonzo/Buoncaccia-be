package it.buonacaccia.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import it.buonacaccia.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import tools.jackson.databind.JsonNode;

/** US2: il proprio profilo (FR-010…FR-012) e i rifiuti dei token non validi (FR-007). */
class UserControllerTest extends ApiTestSupport {

    @Autowired
    private JwtEncoder mockJwtEncoder;

    @Test
    void segreteriaSeesOwnRoleAndEntitlements() {
        JsonNode profile = get("/api/v1/users/me", tokenOf("segreteria")).body().get("payload");

        assertThat(profile.get("buonaCacciaUUID").asString()).isEqualTo("user-002");
        assertThat(texts(profile.get("roles"))).containsExactly("agesci:regione:segreteria");
        assertThat(texts(profile.get("entitlements"))).containsExactlyInAnyOrder(
                "agesci:entitlement:evento:gestione",
                "agesci:entitlement:iscrizione:gestione",
                "agesci:entitlement:pagamento:registrazione");
    }

    @Test
    void socioSeesCensusData() {
        JsonNode profile = get("/api/v1/users/me", tokenOf("socio")).body().get("payload");

        assertThat(profile.get("member").asBoolean()).isTrue();
        JsonNode census = profile.get("census");
        assertThat(census.get("memberCode").asString()).isNotBlank();
        assertThat(census.get("status").asString()).isEqualTo("IN_GOOD_STANDING");
    }

    @Test
    void esternoIsNotAMember() {
        JsonNode profile = get("/api/v1/users/me", tokenOf("esterno")).body().get("payload");

        assertThat(profile.get("member").asBoolean()).isFalse();
        assertThat(profile.get("census").isNull()).isTrue();
    }

    @Test
    void missingTokenIsUnauthenticated() {
        assertUnauthenticated(get("/api/v1/users/me"));
    }

    @Test
    void tamperedTokenIsUnauthenticated() {
        String token = tokenOf("socio");
        String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");

        assertUnauthenticated(get("/api/v1/users/me", tampered));
    }

    @Test
    void malformedTokenIsUnauthenticated() {
        assertUnauthenticated(get("/api/v1/users/me", "non-un-jwt"));
    }

    @Test
    void tokenSignedByAnotherKeyIsUnauthenticated() throws Exception {
        // Simula un token di un avvio precedente: stessa forma, chiave diversa.
        RSAKey otherKey = new RSAKeyGenerator(2048).keyID("vecchia").generate();
        JwtEncoder otherEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(otherKey)));

        assertUnauthenticated(get("/api/v1/users/me", sign(otherEncoder, "mock-auth", Instant.now().plusSeconds(3600), "x")));
    }

    @Test
    void expiredTokenIsUnauthenticated() {
        String token = sign(mockJwtEncoder, "mock-auth", Instant.now().minusSeconds(3600), "x");

        assertUnauthenticated(get("/api/v1/users/me", token));
    }

    @Test
    void unknownIssuerIsUnauthenticated() {
        String token = sign(mockJwtEncoder, "altro-emittente", Instant.now().plusSeconds(3600), "x");

        assertUnauthenticated(get("/api/v1/users/me", token));
    }

    @Test
    void unknownPersonIsCreatedFromTokenClaims() {
        String agesciUUID = UUID.randomUUID().toString();
        String token = sign(mockJwtEncoder, "mock-auth", Instant.now().plusSeconds(3600), agesciUUID);

        Result result = get("/api/v1/users/me", token);

        assertThat(result.status()).isEqualTo(200);
        JsonNode profile = result.body().get("payload");
        assertThat(profile.get("agesciUUID").asString()).isEqualTo(agesciUUID);
        assertThat(profile.get("buonaCacciaUUID").asString()).isNotBlank();
        assertThat(profile.get("firstName").asString()).isEqualTo("Nuova");
        assertThat(texts(profile.get("entitlements"))).containsExactly("agesci:entitlement:iscrizione:self");
        assertThat(profile.get("member").asBoolean()).isFalse();
    }

    private static String sign(JwtEncoder encoder, String issuer, Instant expiresAt, String agesciUUID) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("sub-" + agesciUUID)
                .issuedAt(expiresAt.minusSeconds(7200))
                .expiresAt(expiresAt)
                .claim("agesciUUID", agesciUUID)
                .claim("isMemberOf", "agesci:socio:false")
                .claim("role", List.of("agesci:socio:user"))
                .claim("entitlement", List.of("agesci:entitlement:iscrizione:self"))
                .claim("preferred_username", "nuova-" + agesciUUID)
                .claim("given_name", "Nuova")
                .claim("family_name", "Persona")
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static void assertUnauthenticated(Result result) {
        assertThat(result.status()).isEqualTo(401);
        assertThat(result.body().get("errorCode").asInt()).isEqualTo(40100);
        assertThat(result.body().get("errorDomain").asString()).isEqualTo("AUTH");
        assertThat(result.body().get("payload").isNull()).isTrue();
    }

    private static List<String> texts(JsonNode array) {
        return array.valueStream().map(JsonNode::asString).toList();
    }
}
