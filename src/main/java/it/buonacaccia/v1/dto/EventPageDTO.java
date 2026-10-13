package it.buonacaccia.v1.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Una pagina di eventi (FR-019): convenzione page/size/sort di AGENTS.md. */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class EventPageDTO {

    private List<EventDTO> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
