package it.buonacaccia.service.guard;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import it.buonacaccia.config.error.BuonaCacciaException;
import it.buonacaccia.config.error.ErrorCode;
import it.buonacaccia.model.Branch;
import it.buonacaccia.model.Census;
import it.buonacaccia.model.Enrollment;
import it.buonacaccia.model.EnrollmentObligation;
import it.buonacaccia.model.Entitlements;
import it.buonacaccia.model.Event;
import it.buonacaccia.model.MemberType;
import it.buonacaccia.model.ObligationStatus;
import it.buonacaccia.model.ObligationType;
import it.buonacaccia.model.User;
import it.buonacaccia.repository.UserRepository;
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
    private final UserRepository userRepository;

    public boolean canManage(CallerContext caller, Enrollment enrollment) {
        return caller.hasEntitlement(Entitlements.ISCRIZIONE_GESTIONE)
                && eventAccessPolicy.canManage(caller, enrollment.getEventLevel(), enrollment.getEventRegion());
    }

    public boolean isParticipantOrPresenter(CallerContext caller, Enrollment enrollment) {
        String subject = caller.getSubject();
        return subject != null && (subject.equals(enrollment.getParticipantId()) || subject.equals(enrollment.getPresenterId()));
    }

    /**
     * Chi compila scheda, documenti e pagamento e chi si ritira (decisione di Mahima, 24/10): il presentatore, il capo
     * unità del partecipante e, se non è un lupetto/coccinella o un esploratore/guida, il partecipante stesso.
     * Le iscrizioni di L/C ed E/G le gestisce solo il capo unità.
     */
    public boolean canAct(CallerContext caller, Enrollment enrollment) {
        String subject = caller.getSubject();
        if (subject == null) {
            return false;
        }
        return subject.equals(enrollment.getPresenterId()) || isUnitLeaderOf(subject, enrollment)
                || (subject.equals(enrollment.getParticipantId()) && !managedByLeader(enrollment));
    }

    /** Chi può vedere l'iscrizione come "suo": chi agisce più il partecipante, anche se L/C o E/G. */
    public boolean canSee(CallerContext caller, Enrollment enrollment) {
        return canAct(caller, enrollment) || isParticipantOrPresenter(caller, enrollment);
    }

    /** 404 per chi non la vede, 403 per chi la vede ma non la può gestire (il ragazzo L/C o E/G). */
    public void requireAct(CallerContext caller, Enrollment enrollment) {
        if (canAct(caller, enrollment)) {
            return;
        }
        throw canSee(caller, enrollment)
                ? new BuonaCacciaException(ErrorCode.FORBIDDEN, "L'iscrizione di lupetti, coccinelle, esploratori e guide la gestisce il capo unità.")
                : new BuonaCacciaException(ErrorCode.NOT_FOUND, "Iscrizione non trovata.");
    }

    private static boolean managedByLeader(Enrollment enrollment) {
        return enrollment.getParticipantBranch() == Branch.LC || enrollment.getParticipantBranch() == Branch.EG;
    }

    private boolean isUnitLeaderOf(String subject, Enrollment enrollment) {
        if (enrollment.getParticipantUnitId() == null || subject.equals(enrollment.getParticipantId())) {
            return false;
        }
        return userRepository.findById(subject)
                .map(User::getCensus)
                .filter(c -> c.getMemberType() == MemberType.LEADER && enrollment.getParticipantUnitId().equals(c.getUnitId()))
                .isPresent();
    }

    /** Il chiamante è il capo unità (o il capo gruppo) del partecipante, e non il partecipante stesso. */
    public boolean canAuthorize(User caller, Enrollment enrollment, ObligationType type) {
        if (caller == null || caller.getCensus() == null || caller.getId().equals(enrollment.getParticipantId())) {
            return false;
        }
        Census census = caller.getCensus();
        return switch (type) {
            case UNIT_LEADER_AUTHORIZATION -> (census.getMemberType() == MemberType.LEADER
                    && census.getUnitId() != null && census.getUnitId().equals(enrollment.getParticipantUnitId()))
                    || isGroupLeaderOf(census, enrollment);
            case GROUP_LEADER_AUTHORIZATION -> isGroupLeaderOf(census, enrollment);
            default -> false;
        };
    }

    /** Il capo gruppo autorizza tutte le iscrizioni dei soci del suo gruppo (decisione di Mahima, 24/10). */
    private static boolean isGroupLeaderOf(Census census, Enrollment enrollment) {
        return census.isGroupLeader() && census.getGroupId() != null && census.getGroupId().equals(enrollment.getParticipantGroupId());
    }

    /** Per tutti gli altri l'iscrizione non esiste (404). */
    public boolean canView(CallerContext caller, User callerUser, Enrollment enrollment) {
        return canSee(caller, enrollment)
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
        if (canAct(caller, enrollment) && event != null && event.getStartsAt() != null
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
