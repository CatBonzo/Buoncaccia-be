package it.buonacaccia.service.guard;

import it.buonacaccia.config.error.BuonaCacciaException;
import it.buonacaccia.config.error.ErrorCode;
import it.buonacaccia.model.EventLevel;
import it.buonacaccia.model.Region;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Regola territoriale della gestione eventi (FR-010, Q11), da usare dopo la guardia
 * {@code @SecurityAccess} che controlla l'entitlement {@code evento:gestione}.
 * L'amministrazione gestisce tutto; la segreteria solo eventi regionali o di zona della propria
 * regione; gli eventi nazionali solo l'amministrazione. La consultazione resta libera (FR-011).
 */
@Slf4j
@Service
public class EventAccessPolicy {

    public boolean canManage(CallerContext caller, EventLevel level, Region region) {
        if (caller.isAdmin()) {
            return true;
        }
        if (!caller.isAuthenticated() || level == null || level == EventLevel.NATIONAL) {
            return false;
        }
        return caller.region().map(own -> own == region).orElse(false);
    }

    /** @throws BuonaCacciaException {@code FORBIDDEN} se il chiamante non gestisce eventi di quel livello e regione */
    public void checkCanManage(CallerContext caller, EventLevel level, Region region) {
        if (!canManage(caller, level, region)) {
            log.warn("Gestione negata a {} (regione {}): evento {} {}", caller.getSubject(), caller.getRegion(), level, region);
            throw new BuonaCacciaException(ErrorCode.FORBIDDEN,
                    "Puoi gestire solo eventi regionali o di zona della tua regione.");
        }
    }

    /** @throws BuonaCacciaException {@code FORBIDDEN} se non è amministrazione e non ha una regione valida */
    public void checkManagesSomething(CallerContext caller) {
        if (!caller.isAdmin() && caller.region().isEmpty()) {
            log.warn("Gestione negata a {}: nessuna regione in isMemberOf", caller.getSubject());
            throw new BuonaCacciaException(ErrorCode.FORBIDDEN,
                    "Nel token manca la regione della segreteria (isMemberOf agesci:<SIGLA>:segreteria).");
        }
    }
}
