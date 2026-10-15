package it.buonacaccia.v1.dto;

import java.util.List;

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
public class RefundPageDTO {

    private List<RefundDTO> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
