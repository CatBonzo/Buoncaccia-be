package it.buonacaccia.v1.dto;

import jakarta.validation.Valid;
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
public class ContactsDTO {

    @Valid
    private ContactDTO methodological;
    @Valid
    private ContactDTO administrative;
}
