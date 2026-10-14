package it.buonacaccia.model;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Requisiti di iscrizione (FR-006, BR-06), sotto-documento di evento e tipo evento. In questa
 * feature sono solo salvati e mostrati; li applicheranno le iscrizioni (fase 3).
 * {@code @EqualsAndHashCode} serve a riconoscere un requisito cambiato dopo la pubblicazione (FR-016).
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
@EqualsAndHashCode
public class Requirements {

    /** Età minima alla data di partenza. */
    private Integer minAge;
    /** Età massima alla data di partenza. */
    private Integer maxAge;
    @Builder.Default
    private List<Branch> branches = new ArrayList<>();
    @Builder.Default
    private List<MemberType> memberTypes = new ArrayList<>();
    /** Una sola iscrizione attiva a eventi dello stesso tipo. */
    private boolean oneActivePerType;
    /** Numero massimo di iscritti dello stesso gruppo; vuoto = nessun limite. */
    private Integer maxPerGroup;
}
