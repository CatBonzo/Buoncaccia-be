package it.buonacaccia.config.guard;

import java.util.Collection;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Applica {@link SecurityAccess} prima del metodo del controller, leggendo i claim {@code role} ed
 * {@code entitlement} del token già validato da Spring Security. Le eccezioni diventano 401/403
 * con l'envelope nel gestore globale.
 */
@Slf4j
@Component
public class SecurityGuardInterceptor implements HandlerInterceptor {

    public static final String ROLE_CLAIM = "role";
    public static final String ENTITLEMENT_CLAIM = "entitlement";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        SecurityAccess access = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), SecurityAccess.class);
        if (access == null) {
            access = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), SecurityAccess.class);
        }
        if (access == null) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new InsufficientAuthenticationException("Token assente");
        }
        if (!hasAny(jwt.getClaimAsStringList(ROLE_CLAIM), access.role())
                || !hasAny(jwt.getClaimAsStringList(ENTITLEMENT_CLAIM), access.entitlement())) {
            log.warn("Accesso negato a {} {} per {}: richiesti ruolo {} ed entitlement {}", request.getMethod(),
                    request.getRequestURI(), jwt.getSubject(), List.of(access.role()), List.of(access.entitlement()));
            throw new AccessDeniedException("Permesso mancante");
        }
        return true;
    }

    private static boolean hasAny(Collection<String> granted, String[] required) {
        if (required.length == 0) {
            return true;
        }
        return granted != null && List.of(required).stream().anyMatch(granted::contains);
    }
}
