package it.buonacaccia.v1.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** A chi e come pagare: coordinate della segreteria della regione dell'evento (FR-002 della 005). */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class PaymentInfoDTO {

    private String holder;
    private String iban;
    private String contactEmail;
    private String contactPhone;
    private String hours;
}
