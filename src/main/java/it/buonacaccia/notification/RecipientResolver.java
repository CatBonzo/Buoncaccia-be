package it.buonacaccia.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import it.buonacaccia.model.Enrollment;
import it.buonacaccia.model.MemberType;
import it.buonacaccia.model.NotificationTemplate;
import it.buonacaccia.model.ObligationType;
import it.buonacaccia.model.RecipientRole;
import it.buonacaccia.model.User;
import it.buonacaccia.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * Destinatari di un evento di dominio (tabella delle notifiche, FR-008…FR-013 della 004, research R6).
 * I capi sono gli stessi che possono autorizzare dal sito ({@code EnrollmentAccessPolicy.canAuthorize}).
 */
@Component
@RequiredArgsConstructor
public class RecipientResolver {

    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;

    public List<Recipient> resolve(DomainEvent event) {
        DomainEvent.EnrollmentSnapshot enrollment = event.enrollment();
        return switch (event.type()) {
            case ENROLLMENT_REGISTERED -> participantAndPresenter(enrollment,
                    NotificationTemplate.ENROLLMENT_CONFIRMATION, NotificationTemplate.PRESENTER_NOTICE);
            case AUTHORIZATION_REQUESTED -> leaders(enrollment, event.obligationType());
            case DEADLINE_REMINDER -> participant(enrollment, NotificationTemplate.DEADLINE_REMINDER);
            case ENROLLMENT_COMPLETED -> participantAndPresenter(enrollment, NotificationTemplate.ENROLLMENT_COMPLETED);
            case ENROLLMENT_EXPIRED -> participantAndPresenter(enrollment, NotificationTemplate.ENROLLMENT_EXPIRED);
            case ENROLLMENT_WITHDRAWN -> participantAndPresenter(enrollment, NotificationTemplate.ENROLLMENT_WITHDRAWN);
            case ENROLLMENT_PROMOTED -> participantAndPresenter(enrollment, NotificationTemplate.ENROLLMENT_PROMOTED);
            case ENROLLMENT_REJECTED -> participantAndPresenter(enrollment, NotificationTemplate.ENROLLMENT_REJECTED);
            case ENROLLMENT_CANCELLED -> participantAndPresenter(enrollment, NotificationTemplate.ENROLLMENT_CANCELLED);
            case EVENT_CHANGED -> activeParticipants(event.event().id());
        };
    }

    private List<Recipient> participant(DomainEvent.EnrollmentSnapshot enrollment, NotificationTemplate template) {
        return user(enrollment.participantId())
                .map(u -> List.of(new Recipient(u, RecipientRole.PARTICIPANT, template, enrollment.id())))
                .orElse(List.of());
    }

    private List<Recipient> participantAndPresenter(DomainEvent.EnrollmentSnapshot enrollment,
                                                    NotificationTemplate template) {
        return participantAndPresenter(enrollment, template, template);
    }

    private List<Recipient> participantAndPresenter(DomainEvent.EnrollmentSnapshot enrollment,
                                                    NotificationTemplate participantTemplate,
                                                    NotificationTemplate presenterTemplate) {
        List<Recipient> result = new ArrayList<>(participant(enrollment, participantTemplate));
        String presenterId = enrollment.presenterId();
        if (presenterId != null && !presenterId.equals(enrollment.participantId())) {
            user(presenterId).ifPresent(u ->
                    result.add(new Recipient(u, RecipientRole.PRESENTER, presenterTemplate, enrollment.id())));
        }
        return result;
    }

    /** Capi che possono dare l'autorizzazione, escluso il partecipante stesso (edge case della spec). */
    private List<Recipient> leaders(DomainEvent.EnrollmentSnapshot enrollment, ObligationType type) {
        List<User> leaders;
        RecipientRole role;
        if (type == ObligationType.UNIT_LEADER_AUTHORIZATION) {
            leaders = enrollment.participantUnitId() == null ? List.of()
                    : userRepository.findByCensusUnitIdAndCensusMemberType(enrollment.participantUnitId(), MemberType.LEADER);
            role = RecipientRole.UNIT_LEADER;
        } else if (type == ObligationType.GROUP_LEADER_AUTHORIZATION) {
            leaders = enrollment.participantGroupId() == null ? List.of()
                    : userRepository.findByCensusGroupIdAndCensusGroupLeaderTrue(enrollment.participantGroupId());
            role = RecipientRole.GROUP_LEADER;
        } else {
            return List.of();
        }
        return leaders.stream()
                .filter(u -> !u.getId().equals(enrollment.participantId()))
                .map(u -> new Recipient(u, role, NotificationTemplate.AUTHORIZATION_REQUEST, enrollment.id()))
                .toList();
    }

    /** Iscritti attivi al momento dell'elaborazione (research R6). */
    private List<Recipient> activeParticipants(String eventId) {
        List<Enrollment> active = mongoTemplate.find(
                new Query(Criteria.where("eventId").is(eventId).and("active").is(true)), Enrollment.class);
        Map<String, User> users = userRepository.findAllById(active.stream().map(Enrollment::getParticipantId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return active.stream()
                .filter(e -> users.containsKey(e.getParticipantId()))
                .map(e -> new Recipient(users.get(e.getParticipantId()), RecipientRole.PARTICIPANT,
                        NotificationTemplate.EVENT_CHANGED, e.getId()))
                .toList();
    }

    private Optional<User> user(String id) {
        return id == null ? Optional.empty() : userRepository.findById(id);
    }
}
