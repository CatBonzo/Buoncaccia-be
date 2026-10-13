package it.buonacaccia.service.guard;

import java.util.Collection;
import java.util.List;

import it.buonacaccia.guard.SecurityAccess;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Regola della guardia {@link SecurityAccess} (AUTH-1): il token deve contenere almeno uno dei
 * ruoli e almeno uno degli entitlement richiesti; un elenco vuoto non pone vincoli.
 */
@Service
public class SecurityGuardService {

    public static final String ROLE_CLAIM = "role";
    public static final String ENTITLEMENT_CLAIM = "entitlement";

    public boolean isAllowed(Jwt jwt, SecurityAccess access) {
        return hasAny(jwt.getClaimAsStringList(ROLE_CLAIM), access.role())
                && hasAny(jwt.getClaimAsStringList(ENTITLEMENT_CLAIM), access.entitlement());
    }

    private static boolean hasAny(Collection<String> granted, String[] required) {
        if (required.length == 0) {
            return true;
        }
        return granted != null && List.of(required).stream().anyMatch(granted::contains);
    }
}
