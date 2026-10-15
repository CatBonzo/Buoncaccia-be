package it.buonacaccia.v1.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Rimborso fatto: data (non nel futuro) e riferimento facoltativo (FR-010 della 005). */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class RefundIssueDTO {

    @NotNull
    private LocalDate issuedOn;

    @Size(max = 100)
    private String reference;
}
