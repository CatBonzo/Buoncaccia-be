package it.buonacaccia.v1.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.buonacaccia.service.DocumentService;
import it.buonacaccia.service.guard.CallerContext;
import it.buonacaccia.v1.dto.ApiResponse;
import it.buonacaccia.v1.dto.DocumentDTO;
import it.buonacaccia.v1.mapper.DocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@Tag(name = "documents", description = "Documenti richiesti dagli eventi: caricamento e verifica")
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping("/api/v1/enrollments/{id}/documents")
    @Operation(summary = "Documenti di un'iscrizione, anche sostituiti e rifiutati (FR-013)")
    public ApiResponse<List<DocumentDTO>> list(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(DocumentMapper.INSTANCE.toDtoList(documentService.list(CallerContext.from(jwt), id)));
    }

    @GetMapping("/api/v1/documents/{id}/content")
    @Operation(summary = "Scarica il file con il nome e il tipo originali (FR-013)")
    public ResponseEntity<byte[]> content(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        DocumentService.DocumentContent result = documentService.content(CallerContext.from(jwt), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.document().getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(result.document().getOriginalName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(result.content());
    }

    @PostMapping(value = "/api/v1/enrollments/{id}/obligations/{code}/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Il partecipante o il presentatore carica il documento richiesto (FR-004)")
    public ApiResponse<DocumentDTO> upload(@PathVariable String id, @PathVariable String code,
                                           @RequestPart("file") MultipartFile file, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(DocumentMapper.INSTANCE.toDto(documentService.upload(CallerContext.from(jwt), id, code, file)));
    }
}
