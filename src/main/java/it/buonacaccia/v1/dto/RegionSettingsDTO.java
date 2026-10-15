package it.buonacaccia.v1.dto;

import java.time.Instant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Coordinate di pagamento e contatti di una segreteria (FR-016…FR-018 della 005). */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class RegionSettingsDTO {

    /** Sigla della regione o {@code NAT}; in scrittura vale quella del percorso. */
    private String key;

    @Size(max = 120)
    private String holder;

    @Size(max = 40)
    private String iban;

    @Email
    @Size(max = 120)
    private String contactEmail;

    @Size(max = 40)
    private String contactPhone;

    @Size(max = 300)
    private String hours;

    private Instant updatedAt;
}
