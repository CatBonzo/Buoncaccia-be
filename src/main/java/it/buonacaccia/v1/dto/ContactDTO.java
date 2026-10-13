package it.buonacaccia.v1.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
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
public class ContactDTO {

    @Size(max = 120, message = "Al massimo 120 caratteri.")
    private String name;
    @Email(message = "Indirizzo e-mail non valido.")
    private String email;
    @Size(max = 30, message = "Al massimo 30 caratteri.")
    private String phone;
}
