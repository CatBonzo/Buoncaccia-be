package it.buonacaccia.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.buonacaccia.api.dto.ApiResponse;
import it.buonacaccia.api.dto.UserProfileResponse;
import it.buonacaccia.config.guard.SecurityAccess;
import it.buonacaccia.mapper.UserMapper;
import it.buonacaccia.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "users", description = "Profilo della persona autenticata")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    public UserController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @GetMapping("/me")
    @SecurityAccess
    @Operation(summary = "Profilo, censimento, ruoli ed entitlement della persona autenticata (FR-010)")
    public ApiResponse<UserProfileResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(userMapper.toProfile(userService.syncFromToken(jwt)));
    }
}
