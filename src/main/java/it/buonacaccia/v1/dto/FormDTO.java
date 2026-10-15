package it.buonacaccia.v1.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import it.buonacaccia.model.FormStatus;
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
public class FormDTO {

    private String enrollmentId;
    private String typeCode;
    private FormStatus status;
    private List<FormSectionDTO> sections;
    private Map<String, String> answers;
    private Instant submittedAt;
    private Instant reopenedAt;
    private String reopenReason;
}
