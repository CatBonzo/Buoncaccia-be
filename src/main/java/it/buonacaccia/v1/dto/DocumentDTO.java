package it.buonacaccia.v1.dto;

import java.time.Instant;

import it.buonacaccia.model.DocumentStatus;
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
public class DocumentDTO {

    private String id;
    private String enrollmentId;
    private String enrollmentNumber;
    private String eventId;
    private String eventCode;
    private String participantId;
    private String code;
    private String originalName;
    private String contentType;
    private long sizeBytes;
    private String sha256;
    private DocumentStatus status;
    private String uploadedBy;
    private Instant uploadedAt;
    private String reviewedBy;
    private Instant reviewedAt;
    private String rejectionReason;
}
