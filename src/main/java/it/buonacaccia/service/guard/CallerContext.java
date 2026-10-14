package it.buonacaccia.service.guard;

import java.util.List;
import java.util.Optional;

import it.buonacaccia.model.Region;
import it.buonacaccia.model.Roles;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Chi sta chiamando, letto dal token: {@code sub}, se è amministrazione e quale regione gestisce.
 * La regione viene da {@code isMemberOf} nella forma {@code agesci:<SIGLA>:segreteria} (FR-010,
 * research R2); il claim può essere una stringa o una lista. Senza token è un visitatore anonimo.
 */
@Getter
@ToString
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class CallerContext {

    public static final String IS_MEMBER_OF_CLAIM = "isMemberOf";
    private static final String MEMBERSHIP_PREFIX = "agesci:";
    private static final String MEMBERSHIP_SUFFIX = ":segreteria";

    private static final CallerContext ANONYMOUS = new CallerContext(null, false, null);

    private final String subject;
    private final boolean admin;
    /** Regione della segreteria; {@code null} se manca o la sigla è sconosciuta. */
    private final Region region;

    public static CallerContext anonymous() {
        return ANONYMOUS;
    }

    public static CallerContext from(Jwt jwt) {
        if (jwt == null) {
            return ANONYMOUS;
        }
        List<String> roles = jwt.getClaimAsStringList(SecurityGuardService.ROLE_CLAIM);
        boolean admin = roles != null && roles.contains(Roles.AMMINISTRATORE);
        return new CallerContext(jwt.getSubject(), admin, regionOf(jwt).orElse(null));
    }

    public boolean isAuthenticated() {
        return subject != null;
    }

    public Optional<Region> region() {
        return Optional.ofNullable(region);
    }

    private static Optional<Region> regionOf(Jwt jwt) {
        List<String> memberships = jwt.getClaimAsStringList(IS_MEMBER_OF_CLAIM);
        if (memberships == null) {
            return Optional.empty();
        }
        return memberships.stream()
                .filter(value -> value.startsWith(MEMBERSHIP_PREFIX) && value.endsWith(MEMBERSHIP_SUFFIX))
                .map(value -> value.substring(MEMBERSHIP_PREFIX.length(), value.length() - MEMBERSHIP_SUFFIX.length()))
                .map(Region::fromCode)
                .flatMap(Optional::stream)
                .findFirst();
    }
}
