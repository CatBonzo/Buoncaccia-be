package it.buonacaccia.v1.dto;

import java.time.Instant;

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
public class EventAttachmentDTO {

    private String id;
    private String eventId;
    private String name;
    private String contentType;
    private long sizeBytes;
    private Instant uploadedAt;
}
