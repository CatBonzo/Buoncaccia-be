package it.buonacaccia.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import it.buonacaccia.config.error.BuonaCacciaException;
import it.buonacaccia.config.error.ErrorCode;
import it.buonacaccia.model.Event;
import it.buonacaccia.model.EventLevel;
import it.buonacaccia.v1.dto.EventRequestDTO;
import it.buonacaccia.v1.dto.FieldErrorDTO;
import it.buonacaccia.v1.mapper.EventMapper;
import it.buonacaccia.v1.mapper.FormMapper;
import org.springframework.stereotype.Component;

/**
 * Regole sui dati dell'evento: gli invarianti per pubblicare (FR-007), raccolti tutti prima di
 * rispondere, e i campi che non si cambiano più dopo la pubblicazione (FR-016).
 */
@Component
public class EventValidator {

    /**
     * Controlla gli invarianti. Il tipo deve solo esistere: una bozza resta pubblicabile anche se il
     * tipo è stato disattivato dopo la sua creazione (caso limite della spec).
     *
     * @throws BuonaCacciaException {@code PUBLISH_INVARIANTS} con l'elenco dei campi da correggere
     */
    public void checkInvariants(Event event, boolean typeExists) {
        List<FieldErrorDTO> errors = new ArrayList<>();
        if (!typeExists) {
            add(errors, "typeCode", "Il tipo evento non esiste.");
        }
        if (isBlank(event.getTitle())) {
            add(errors, "title", "Il titolo è obbligatorio.");
        }
        if (isBlank(event.getLocation())) {
            add(errors, "location", "La località è obbligatoria.");
        }
        if (event.getLevel() != EventLevel.NATIONAL && event.getRegion() == null) {
            add(errors, "region", "La regione è obbligatoria salvo per gli eventi nazionali.");
        }
        boolean zoneLevel = event.getLevel() == EventLevel.ZONE;
        if (zoneLevel == isBlank(event.getZone())) {
            add(errors, "zone", zoneLevel ? "La zona è obbligatoria per gli eventi di zona."
                    : "La zona si indica solo per gli eventi di zona.");
        }
        checkDates(event, errors);
        if (event.getSeats() == null || event.getSeats() <= 0) {
            add(errors, "seats", "I posti devono essere più di zero.");
        }
        if (event.getFee() == null || event.getFee().signum() < 0) {
            add(errors, "fee", "La quota è obbligatoria e non può essere negativa (0 è ammesso).");
        }
        if (event.getEnrollmentMode() == null) {
            add(errors, "enrollmentMode", "Indica chi può iscrivere.");
        }
        if (event.getRequirements() != null && event.getRequirements().getMinAge() != null
                && event.getRequirements().getMaxAge() != null
                && event.getRequirements().getMinAge() > event.getRequirements().getMaxAge()) {
            add(errors, "requirements.maxAge", "L'età massima non può essere minore di quella minima.");
        }
        if (!errors.isEmpty()) {
            throw new BuonaCacciaException(ErrorCode.PUBLISH_INVARIANTS, ErrorCode.PUBLISH_INVARIANTS.defaultMessage(), errors);
        }
    }

    /**
     * Dopo la pubblicazione restano bloccati tipo, livello, regione, zona, data di apertura, modalità,
     * requisiti e adempimenti. Un campo assente nella richiesta vale come non cambiato.
     *
     * @throws BuonaCacciaException {@code FIELD_LOCKED} col nome del primo campo cambiato
     */
    public void checkLockedFields(Event current, EventRequestDTO request) {
        lock("typeCode", current.getTypeCode(), request.getTypeCode());
        lock("level", current.getLevel(), request.getLevel());
        lock("region", current.getRegion(), request.getRegion());
        lock("zone", current.getZone(), request.getZone());
        lock("registrationOpenDate", current.getRegistrationOpenDate(), request.getRegistrationOpenDate());
        lock("enrollmentMode", current.getEnrollmentMode(), request.getEnrollmentMode());
        if (request.getRequirements() != null) {
            lock("requirements", current.getRequirements(), EventMapper.INSTANCE.toRequirements(request.getRequirements()));
        }
        if (request.getMedicalFormRequired() != null) {
            lock("medicalFormRequired", current.isMedicalFormRequired(), request.getMedicalFormRequired());
        }
        if (request.getDietaryInfoRequired() != null) {
            lock("dietaryInfoRequired", current.isDietaryInfoRequired(), request.getDietaryInfoRequired());
        }
        if (request.getFormSections() != null) {
            lock("formSections", String.valueOf(current.getFormSections()), String.valueOf(FormMapper.INSTANCE.toSections(request.getFormSections())));
        }
        if (request.getObligations() != null) {
            lock("obligations", current.getObligations(), EventMapper.INSTANCE.toObligations(request.getObligations()));
        }
    }

    private static void checkDates(Event event, List<FieldErrorDTO> errors) {
        if (event.getStartDate() == null) {
            add(errors, "startDate", "La data di partenza è obbligatoria.");
        }
        if (event.getEndDate() == null) {
            add(errors, "endDate", "La data di rientro è obbligatoria.");
        }
        if (event.getRegistrationOpenDate() == null) {
            add(errors, "registrationOpenDate", "La data di apertura delle iscrizioni è obbligatoria.");
        }
        if (event.getRegistrationCloseDate() == null) {
            add(errors, "registrationCloseDate", "La data di chiusura delle iscrizioni è obbligatoria.");
        }
        if (event.getStartDate() != null && event.getEndDate() != null && event.getEndDate().isBefore(event.getStartDate())) {
            add(errors, "endDate", "Il rientro non può precedere la partenza.");
        }
        if (event.getRegistrationOpenDate() != null && event.getRegistrationCloseDate() != null
                && !event.getRegistrationOpenDate().isBefore(event.getRegistrationCloseDate())) {
            add(errors, "registrationCloseDate", "La chiusura delle iscrizioni deve seguire l'apertura.");
        } else if (event.getRegistrationCloseDate() != null && event.getStartDate() != null
                && event.getRegistrationCloseDate().isAfter(event.getStartDate())) {
            add(errors, "registrationCloseDate", "Le iscrizioni devono chiudersi entro la partenza.");
        }
    }

    private static void lock(String field, Object current, Object requested) {
        if (requested != null && !Objects.equals(current, requested)) {
            throw new BuonaCacciaException(ErrorCode.FIELD_LOCKED,
                    "Il campo " + field + " non è modificabile dopo la pubblicazione.");
        }
    }

    private static void add(List<FieldErrorDTO> errors, String field, String message) {
        errors.add(new FieldErrorDTO(field, message));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
