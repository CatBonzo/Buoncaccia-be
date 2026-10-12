package it.buonacaccia.config;

import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Emittente dei token mock (profilo {@code mocked}, FR-003, research R1-R2): a ogni avvio una nuova
 * chiave RSA, quindi i token di un avvio precedente non passano più la verifica della firma.
 * Fuori dal mock il {@link JwtDecoder} viene dalla configurazione standard (Keycloak, fase 6).
 */
@Configuration(proxyBeanMethods = false)
@Profile("mocked")
@EnableConfigurationProperties(MockedIdentityProperties.class)
public class MockAuthConfig {

    public static final String ISSUER = "mock-auth";

    @Bean
    RSAKey mockSigningKey() throws JOSEException {
        return new RSAKeyGenerator(2048)
                .keyID(UUID.randomUUID().toString())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .generate();
    }

    @Bean
    JwtEncoder mockJwtEncoder(RSAKey mockSigningKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(mockSigningKey)));
    }

    @Bean
    JwtDecoder mockJwtDecoder(RSAKey mockSigningKey) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(mockSigningKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return decoder;
    }
}
