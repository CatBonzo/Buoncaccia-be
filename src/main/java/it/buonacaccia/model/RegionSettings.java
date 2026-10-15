package it.buonacaccia.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Coordinate di pagamento e contatti della segreteria (collezione {@code region_settings}, feature 005,
 * research R10). L'{@code id} è la sigla della regione oppure {@code NAT} per l'amministrazione nazionale.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
@Document("region_settings")
public class RegionSettings {

    public static final String NATIONAL = "NAT";

    @Id
    private String id;

    private String holder;
    private String iban;
    private String contactEmail;
    private String contactPhone;
    private String hours;

    private String updatedBy;
    private Instant updatedAt;
}
