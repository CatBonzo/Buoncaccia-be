package it.buonacaccia.v1.dto;

import it.buonacaccia.model.Branch;
import it.buonacaccia.model.CensusStatus;
import it.buonacaccia.model.MemberType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Dati di censimento nel profilo. */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class CensusResponse {

    private String memberCode;
    private String groupId;
    private String unitId;
    private Branch branch;
    private MemberType memberType;
    private CensusStatus status;
    private Integer year;
}
