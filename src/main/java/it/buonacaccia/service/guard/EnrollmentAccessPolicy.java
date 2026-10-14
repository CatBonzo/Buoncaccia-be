package it.buonacaccia.service.guard;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import it.buonacaccia.model.Census;
import it.buonacaccia.model.Enrollment;
import it.buonacaccia.model.EnrollmentObligation;
import it.buonacaccia.model.Entitlements;
import it.buonacaccia.model.Event;
import it.buonacaccia.model.MemberType;
import it.buonacaccia.model.ObligationStatus;
import it.buonacaccia.model.ObligationType;
import it.buonacaccia.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Chi vede e chi agisce su un'iscrizione (FR-009, FR-010, FR-018, FR-021): il partecipante, il
 * presentatore, il capo unità e il capo gruppo del partecipante per le loro autorizzazioni, e chi
 * gestisce l'evento (entitlement {@code iscrizione:gestione} più la regola territoriale della 002).
 * È la relazione con la risorsa (AUTH-2): non si può esprimere in {@code @SecurityAccess}.
 */
@Service
@RequiredArgsConstructor
public class EnrollmentAccessPolicy {

    public static final String WITHDRAW = "WITHDRAW";
    public static final String SATISFY = "SATISFY";

    private final EventAccessPolicy eventAccessPolicy;

    public boolean canManage(CallerContext caller, Enrollment enrollment) {
        return caller.hasEntitlement(Entitlements.ISCRIZIONE_GESTIONE)
                && eventAccessPolicy.canManage(caller, enrollment.getEventLevel(), enrollment.getEventRegion());
    }

    public boolean isParticipantOrPresenter(CallerContext caller, Enrollment enrollment) {
        String subject = caller.getSubject();
        return subject != null && (subject.equals(enrollment.getParticipantId()) || subject.equals(enrollment.getPresenterId()));
    }

    /** Il chiamante è il capo unità (o il capo gruppo) del partecipante, e non il partecipante stesso. */
    public boolean canAuthorize(User caller, Enrollment enrollment, ObligationType type) {
        if (caller == null || caller.getCensus() == null || caller.getId().equals(enrollment.getParticipantId())) {
            return false;
        }
        Census census = caller.getCensus();
        return switch (type) {
            case UNIT_LEADER_AUTHORIZATION -> census.getMemberType() == MemberType.LEADER
                    && census.getUnitId() != null && census.getUnitId().equals(enrollment.getParticipantUnitId());
            case GROUP_LEADER_AUTHORIZATION -> census.isGroupLeader()
                    && census.getGroupId() != null && census.getGroupId().equals(enrollment.getParticipantGroupId());
            default -> false;
        };
    }

    /** Per tutti gli altri l'iscrizione non esiste (404). */
    public boolean canView(CallerContext caller, User callerUser, Enrollment enrollment) {
        return isParticipantOrPresenter(caller, enrollment)
                || canManage(caller, enrollment)
                || enrollment.getObligations().stream()
                        .anyMatch(o -> canAuthorize(callerUser, enrollment, o.getType()));
    }

    /** Azioni che il chiamante può fare ora (FR-021). */
    public List<String> availableActions(CallerContext caller, User callerUser, Enrollment enrollment, Event event,
                                         Instant now) {
        List<String> actions = new ArrayList<>();
        if (!enrollment.isActive()) {
            return actions;
        }
        if (isParticipantOrPresenter(caller, enrollment) && event != null && event.getStartsAt() != null
                && now.isBefore(event.getStartsAt())) {
            actions.add(WITHDRAW);
        }
        for (EnrollmentObligation obligation : enrollment.getObligations()) {
            if (obligation.getStatus() == ObligationStatus.TODO && canAuthorize(callerUser, enrollment, obligation.getType())) {
                String who = obligation.getType() == ObligationType.UNIT_LEADER_AUTHORIZATION ? "UNIT_LEADER" : "GROUP_LEADER";
                actions.add("APPROVE_" + who);
                actions.add("REJECT_" + who);
            }
        }
        if (canManage(caller, enrollment)
                && enrollment.getObligations().stream().anyMatch(o -> o.getStatus() == ObligationStatus.TODO)) {
            actions.add(SATISFY);
        }
        return actions;
    }
}
