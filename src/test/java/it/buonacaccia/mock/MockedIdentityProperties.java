package it.buonacaccia.mock;

import java.util.List;

import it.buonacaccia.model.Branch;
import it.buonacaccia.model.CensusStatus;
import it.buonacaccia.model.MemberType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Utenti di prova letti da {@code mocked-identity.yml} (solo profilo {@code mocked}, research R6).
 * Una voce mancante o incompleta ferma l'avvio con il nome della proprietà sbagliata.
 */
@Validated
@ConfigurationProperties("mocked-identity")
public record MockedIdentityProperties(@NotEmpty List<@Valid MockedUser> users) {

    public record MockedUser(
            @NotBlank String agesciUUID,
            String buonaCacciaUUID,
            @NotBlank String label,
            @NotBlank String username,
            @NotBlank String firstName,
            @NotBlank String lastName,
            String email,
            @NotNull @Valid Authentication authentication,
            @Valid CensusData census) {
    }

    public record Authentication(
            @NotBlank String isMemberOf,
            @NotEmpty List<String> role,
            List<String> entitlement) {

        public Authentication {
            entitlement = entitlement == null ? List.of() : List.copyOf(entitlement);
        }
    }

    public record CensusData(
            @NotBlank String memberCode,
            String groupId,
            String unitId,
            @NotNull Branch branch,
            @NotNull MemberType memberType,
            @NotNull CensusStatus status,
            @NotNull Integer year) {
    }
}
