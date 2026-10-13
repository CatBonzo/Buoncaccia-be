package it.buonacaccia.v1.dto;

import java.util.List;

import it.buonacaccia.model.Branch;
import it.buonacaccia.model.MemberType;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class RequirementsDTO {

    @Min(value = 0, message = "L'età non può essere negativa.")
    private Integer minAge;
    @Min(value = 0, message = "L'età non può essere negativa.")
    private Integer maxAge;
    private List<Branch> branches;
    private List<MemberType> memberTypes;
    private Boolean oneActivePerType;
    @Min(value = 1, message = "Almeno 1, oppure lascia vuoto.")
    private Integer maxPerGroup;
}
