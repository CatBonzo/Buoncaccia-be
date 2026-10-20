package it.buonacaccia.v1.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.buonacaccia.guard.SecurityAccess;
import it.buonacaccia.model.Entitlements;
import it.buonacaccia.model.EventAttachment;
import it.buonacaccia.service.EventAttachmentService;
import it.buonacaccia.service.guard.CallerContext;
import it.buonacaccia.v1.dto.ApiResponse;
import it.buonacaccia.v1.dto.EventAttachmentDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@Tag(name = "event-attachments", description = "File allegati agli eventi (programma, regolamento…)")
public class EventAttachmentController {

    private final EventAttachmentService service;

    @GetMapping("/api/v1/events/{id}/attachments")
    @Operation(summary = "Allegati di un evento: pubblici per gli eventi pubblicati")
    public ApiResponse<List<EventAttachmentDTO>> list(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(service.list(CallerContext.from(jwt), id).stream().map(EventAttachmentController::toDto).toList());
    }

    @GetMapping("/api/v1/events/{id}/attachments/{attachmentId}/content")
    @Operation(summary = "Scarica un allegato con il nome e il tipo originali",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Il file; il tipo vero è nell'intestazione Content-Type",
                    content = @Content(mediaType = "application/octet-stream", schema = @Schema(type = "string", format = "binary"))))
    public ResponseEntity<byte[]> content(@PathVariable String id, @PathVariable String attachmentId,
                                          @AuthenticationPrincipal Jwt jwt) {
        EventAttachmentService.AttachmentContent result = service.content(CallerContext.from(jwt), id, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.attachment().getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(result.attachment().getName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(result.content());
    }

    @PostMapping(value = "/api/v1/events/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityAccess(entitlement = Entitlements.EVENTO_GESTIONE)
    @Operation(summary = "La segreteria allega un file (JPEG, PNG o PDF, fino a 10 MB, al massimo 10 per evento)")
    public ApiResponse<EventAttachmentDTO> upload(@PathVariable String id, @RequestPart("file") MultipartFile file,
                                                  @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(toDto(service.upload(CallerContext.from(jwt), id, file)));
    }

    @DeleteMapping("/api/v1/events/{id}/attachments/{attachmentId}")
    @SecurityAccess(entitlement = Entitlements.EVENTO_GESTIONE)
    @Operation(summary = "La segreteria toglie un allegato")
    public ApiResponse<Void> delete(@PathVariable String id, @PathVariable String attachmentId, @AuthenticationPrincipal Jwt jwt) {
        service.delete(CallerContext.from(jwt), id, attachmentId);
        return ApiResponse.ok(null);
    }

    private static EventAttachmentDTO toDto(EventAttachment a) {
        return EventAttachmentDTO.builder().id(a.getId()).eventId(a.getEventId()).name(a.getName())
                .contentType(a.getContentType()).sizeBytes(a.getSizeBytes()).uploadedAt(a.getUploadedAt()).build();
    }
}
