package it.buonacaccia.v1.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Una pagina di iscrizioni, con la stessa convenzione delle pagine di eventi. */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class EnrollmentPageDTO {

    private List<EnrollmentDTO> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
