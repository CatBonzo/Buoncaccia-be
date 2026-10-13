package it.buonacaccia.v1.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.buonacaccia.v1.dto.ApiResponse;
import it.buonacaccia.v1.dto.UserProfileDTO;
import it.buonacaccia.guard.SecurityAccess;
import it.buonacaccia.v1.mapper.UserMapper;
import it.buonacaccia.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
@Tag(name = "users", description = "Profilo della persona autenticata")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @SecurityAccess
    @Operation(summary = "Profilo, censimento, ruoli ed entitlement della persona autenticata (FR-010)")
    public ApiResponse<UserProfileDTO> me(@AuthenticationPrincipal Jwt jwt) {
        log.debug("GET /users/me per agesciUUID {}", jwt.getClaimAsString("agesciUUID"));
        return ApiResponse.ok(UserMapper.INSTANCE.toDto(userService.syncFromToken(jwt)));
    }
}
