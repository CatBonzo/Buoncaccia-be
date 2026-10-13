package it.buonacaccia.v1.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Versione letta dell'evento, per pubblicare o chiudere senza perdere modifiche altrui (FR-018). */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class VersionDTO {

    private Long version;
}
