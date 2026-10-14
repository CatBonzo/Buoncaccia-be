package it.buonacaccia.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import it.buonacaccia.config.ClockConfig;
import it.buonacaccia.config.error.BuonaCacciaException;
import it.buonacaccia.config.error.ErrorCode;
import it.buonacaccia.config.mongo.CircuitBreakerService;
import it.buonacaccia.model.Branch;
import it.buonacaccia.model.Census;
import it.buonacaccia.model.CensusStatus;
import it.buonacaccia.model.EnrollmentMode;
import it.buonacaccia.model.Event;
import it.buonacaccia.model.MemberType;
import it.buonacaccia.model.Requirements;
import it.buonacaccia.model.User;
import it.buonacaccia.repository.EnrollmentRepository;
import it.buonacaccia.v1.dto.FieldErrorDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Requisiti per iscriversi (BR-01, BR-02, BR-06, research R6): raccoglie tutti i motivi di rifiuto e
 * risponde una volta sola con 422 {@code 42210}. La regione del socio non conta mai (BR-15).
 */
@Component
@RequiredArgsConstructor
public class EnrollmentEligibility {

    private final EnrollmentRepository enrollmentRepository;
    private final CircuitBreakerService circuitBreakerService;

    /** @throws BuonaCacciaException {@code NOT_ELIGIBLE} con l'elenco dei motivi */
    public void check(User participant, boolean byLeader, Event event, Instant now) {
        List<FieldErrorDTO> reasons = personalReasons(participant, byLeader, event,
                now.atZone(ClockConfig.ROME).toLocalDate());
        Requirements requirements = event.getRequirements();
        if (requirements != null && participant.getCensus() != null) {
            if (requirements.isOneActivePerType() && mongo(() -> enrollmentRepository
                    .existsByParticipantIdAndEventTypeCodeAndActiveTrue(participant.getId(), event.getTypeCode()))) {
                reasons.add(new FieldErrorDTO("oneActivePerType",
                        "Il socio ha già un'iscrizione attiva a un evento di questo tipo."));
            }
            String groupId = participant.getCensus().getGroupId();
            if (requirements.getMaxPerGroup() != null && groupId != null) {
                long enrolled = mongo(() -> enrollmentRepository
                        .countByEventIdAndParticipantGroupIdAndActiveTrue(event.getId(), groupId));
                if (enrolled >= requirements.getMaxPerGroup()) {
                    reasons.add(new FieldErrorDTO("maxPerGroup", "Il gruppo ha già %d iscritti a questo evento: il massimo è %d."
                            .formatted(enrolled, requirements.getMaxPerGroup())));
                }
            }
        }
        if (!reasons.isEmpty()) {
            throw new BuonaCacciaException(ErrorCode.NOT_ELIGIBLE, ErrorCode.NOT_ELIGIBLE.defaultMessage(), reasons);
        }
    }

    /** Requisiti che dipendono solo dalla persona e dall'evento, alla data {@code today} (ora italiana). */
    public static List<FieldErrorDTO> personalReasons(User participant, boolean byLeader, Event event, LocalDate today) {
        List<FieldErrorDTO> reasons = new ArrayList<>();
        Census census = participant.getCensus();
        if (census == null) {
            reasons.add(new FieldErrorDTO("census", "La persona non risulta socia censita AGESCI."));
            return reasons;
        }
        if (census.getStatus() != CensusStatus.IN_GOOD_STANDING || census.getYear() == null
                || census.getYear() != today.getYear()) {
            reasons.add(new FieldErrorDTO("census", "Il censimento non è in regola per il %d.".formatted(today.getYear())));
        }
        modeReason(census, byLeader, event.getEnrollmentMode()).ifPresent(reasons::add);

        Requirements requirements = event.getRequirements();
        if (requirements == null) {
            return reasons;
        }
        if (requirements.getMinAge() != null || requirements.getMaxAge() != null) {
            if (participant.getBirthDate() == null || event.getStartDate() == null) {
                reasons.add(new FieldErrorDTO("birthDate", "Manca la data di nascita: serve per i limiti di età dell'evento."));
            } else {
                int age = Period.between(participant.getBirthDate(), event.getStartDate()).getYears();
                if (requirements.getMinAge() != null && age < requirements.getMinAge()) {
                    reasons.add(new FieldErrorDTO("age", "Alla partenza il socio ha %d anni: l'evento chiede almeno %d anni."
                            .formatted(age, requirements.getMinAge())));
                } else if (requirements.getMaxAge() != null && age > requirements.getMaxAge()) {
                    reasons.add(new FieldErrorDTO("age", "Alla partenza il socio ha %d anni: l'evento ammette al massimo %d anni."
                            .formatted(age, requirements.getMaxAge())));
                }
            }
        }
        List<Branch> branches = requirements.getBranches();
        if (branches != null && !branches.isEmpty() && !branches.contains(census.getBranch())) {
            reasons.add(new FieldErrorDTO("branch", "La branca %s non è ammessa: l'evento è per %s."
                    .formatted(census.getBranch(), join(branches))));
        }
        List<MemberType> memberTypes = requirements.getMemberTypes();
        if (memberTypes != null && !memberTypes.isEmpty() && !memberTypes.contains(census.getMemberType())) {
            reasons.add(new FieldErrorDTO("memberType", "Il tipo socio %s non è ammesso: l'evento è per %s."
                    .formatted(census.getMemberType(), join(memberTypes))));
        }
        return reasons;
    }

    /** BR-02 e modalità dell'evento: L/C ed E/G li iscrive il capo; R/S e capi anche da soli. */
    private static Optional<FieldErrorDTO> modeReason(Census census, boolean byLeader, EnrollmentMode mode) {
        EnrollmentMode effective = mode == null ? EnrollmentMode.BOTH : mode;
        if (byLeader) {
            return effective == EnrollmentMode.SELF
                    ? Optional.of(new FieldErrorDTO("enrollmentMode", "Questo evento accetta solo iscrizioni personali."))
                    : Optional.empty();
        }
        if (effective == EnrollmentMode.BY_LEADER) {
            return Optional.of(new FieldErrorDTO("enrollmentMode",
                    "Questo evento accetta solo iscrizioni fatte dal capo unità."));
        }
        boolean canEnrollSelf = census.getMemberType() == MemberType.LEADER || census.getBranch() == Branch.RS;
        return canEnrollSelf ? Optional.empty()
                : Optional.of(new FieldErrorDTO("enrollmentMode",
                        "Lupetti, coccinelle, esploratori e guide li iscrive il loro capo unità."));
    }

    private static String join(List<?> values) {
        return values.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    private <T> T mongo(Supplier<T> operation) {
        return circuitBreakerService.execute(CircuitBreakerService.MONGODB, operation);
    }
}
