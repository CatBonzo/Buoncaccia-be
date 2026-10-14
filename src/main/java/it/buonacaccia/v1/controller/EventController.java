package it.buonacaccia.v1.controller;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.buonacaccia.guard.SecurityAccess;
import it.buonacaccia.model.Availability;
import it.buonacaccia.model.Entitlements;
import it.buonacaccia.model.EventCategory;
import it.buonacaccia.model.EventStatus;
import it.buonacaccia.model.Region;
import it.buonacaccia.service.EventService;
import it.buonacaccia.service.guard.CallerContext;
import it.buonacaccia.v1.dto.ApiResponse;
import it.buonacaccia.v1.dto.CancelDTO;
import it.buonacaccia.v1.dto.EventDTO;
import it.buonacaccia.v1.dto.EventPageDTO;
import it.buonacaccia.v1.dto.EventRequestDTO;
import it.buonacaccia.v1.dto.VersionDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/events")
@Tag(name = "events", description = "Catalogo pubblico e gestione degli eventi")
public class EventController {

    private final EventService eventService;

    @GetMapping
    @Operation(summary = "Ricerca pubblica degli eventi pubblicati (FR-019)")
    public ApiResponse<EventPageDTO> search(@RequestParam(required = false) Region region,
                                            @RequestParam(required = false) EventCategory category,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(required = false) Availability availability,
                                            @RequestParam(required = false) EventStatus status,
                                            @RequestParam(required = false) LocalDate startFrom,
                                            @RequestParam(required = false) LocalDate startTo,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size,
                                            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(eventService.search(region, category, type, availability, status,
                startFrom, startTo, q, page, size, sort));
    }

    @GetMapping("/managed")
    @SecurityAccess(entitlement = Entitlements.EVENTO_GESTIONE)
    @Operation(summary = "Eventi gestibili dal chiamante in tutti gli stati, bozze comprese (FR-009)")
    public ApiResponse<EventPageDTO> managed(@RequestParam(required = false) EventStatus status,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size,
                                             @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(eventService.findManaged(CallerContext.from(jwt), status, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Dettaglio (pubblico; una bozza solo per chi la gestisce, altrimenti 404)")
    public ApiResponse<EventDTO> get(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(eventService.getDetail(CallerContext.from(jwt), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityAccess(entitlement = Entitlements.EVENTO_GESTIONE)
    @Operation(summary = "Crea un evento in bozza (FR-004, regione della segreteria)")
    public ApiResponse<EventDTO> create(@Valid @RequestBody EventRequestDTO request, @AuthenticationPrincipal Jwt jwt) {
        log.debug("POST /events tipo {} livello {} regione {}", request.getTypeCode(), request.getLevel(), request.getRegion());
        return ApiResponse.ok(eventService.create(CallerContext.from(jwt), request));
    }

    @PutMapping("/{id}")
    @SecurityAccess(entitlement = Entitlements.EVENTO_GESTIONE)
    @Operation(summary = "Modifica (bozza tutti i campi; pubblicato solo i campi consentiti, FR-016)")
    public ApiResponse<EventDTO> update(@PathVariable String id, @Valid @RequestBody EventRequestDTO request,
                                        @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(eventService.update(CallerContext.from(jwt), id, request));
    }

    @PostMapping("/{id}/publish")
    @SecurityAccess(entitlement = Entitlements.EVENTO_GESTIONE)
    @Operation(summary = "Pubblica una bozza se rispetta gli invarianti (FR-007)")
    public ApiResponse<EventDTO> publish(@PathVariable String id, @RequestBody(required = false) VersionDTO request,
                                         @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(eventService.publish(CallerContext.from(jwt), id, versionOf(request)));
    }

    @PostMapping("/{id}/close-registrations")
    @SecurityAccess(entitlement = Entitlements.EVENTO_GESTIONE)
    @Operation(summary = "Chiusura anticipata delle iscrizioni (FR-015)")
    public ApiResponse<EventDTO> closeRegistrations(@PathVariable String id,
                                                    @RequestBody(required = false) VersionDTO request,
                                                    @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(eventService.closeRegistrations(CallerContext.from(jwt), id, versionOf(request)));
    }

    @PostMapping("/{id}/cancel")
    @SecurityAccess(entitlement = Entitlements.EVENTO_GESTIONE)
    @Operation(summary = "Annullamento motivato (FR-017)")
    public ApiResponse<EventDTO> cancel(@PathVariable String id, @Valid @RequestBody CancelDTO request,
                                        @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(eventService.cancel(CallerContext.from(jwt), id, request));
    }

    private static Long versionOf(VersionDTO request) {
        return request == null ? null : request.getVersion();
    }
}
