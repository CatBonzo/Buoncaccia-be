package it.buonacaccia.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import it.buonacaccia.config.error.BuonaCacciaException;
import it.buonacaccia.config.error.ErrorCode;
import it.buonacaccia.config.mongo.CircuitBreakerService;
import it.buonacaccia.model.DomainEventType;
import it.buonacaccia.model.Enrollment;
import it.buonacaccia.model.EnrollmentObligation;
import it.buonacaccia.model.EnrollmentStatus;
import it.buonacaccia.model.Event;
import it.buonacaccia.model.FormStatus;
import it.buonacaccia.model.FormSubmission;
import it.buonacaccia.model.ObligationStatus;
import it.buonacaccia.model.ObligationType;
import it.buonacaccia.notification.DomainEventRecorder;
import it.buonacaccia.repository.EnrollmentRepository;
import it.buonacaccia.repository.EventRepository;
import it.buonacaccia.repository.FormSubmissionRepository;
import it.buonacaccia.service.guard.CallerContext;
import it.buonacaccia.service.guard.EventAccessPolicy;
import it.buonacaccia.v1.dto.FormDTO;
import it.buonacaccia.v1.mapper.FormMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Scheda elettronica di un'iscrizione (feature 007). Ogni scrittura rilegge iscrizione e scheda e salva
 * l'iscrizione con la sua versione nella stessa transazione: due richieste insieme non lasciano stati
 * incoerenti, la seconda riprova una volta (research R4). Le risposte non finiscono mai in e-mail né nei log (FR-010).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormService {

    /** Dopo la riapertura il partecipante ha almeno questo tempo per correggere. */
    private static final Duration MIN_TIME_AFTER_REOPEN = Duration.ofDays(3);

    private final FormSubmissionRepository submissionRepository;
    private final FormTemplateService templateService;
    private final EnrollmentRepository enrollmentRepository;
    private final EventRepository eventRepository;
    private final EventAccessPolicy eventAccessPolicy;
    private final DomainEventRecorder domainEvents;
    private final CircuitBreakerService circuitBreakerService;
    private final TransactionRunner transactions;
    private final Clock clock;

    /**
     * Il partecipante vede la sua scheda (modello e risposte); la segreteria dell'evento solo quando è inviata.
     * Per tutti gli altri la scheda non esiste.
     */
    public FormDTO get(CallerContext caller, String enrollmentId) {
        Enrollment enrollment = find(enrollmentId);
        obligationOf(enrollment);
        FormSubmission stored = mongo(() -> submissionRepository.findByEnrollmentId(enrollmentId)).orElse(null);
        if (isParticipant(caller, enrollment)) {
            return toDto(enrollment, stored != null ? stored : draftOf(enrollment));
        }
        if (stored != null && stored.getStatus() == FormStatus.SUBMITTED && canManage(caller, enrollment)) {
            return toDto(enrollment, stored);
        }
        throw notFound();
    }

    /**
     * Salva la bozza: le risposte sostituiscono le precedenti.
     *
     * @throws BuonaCacciaException {@code FORBIDDEN} per la segreteria, {@code NOT_FOUND} per gli altri,
     *                              {@code ENROLLMENT_STATE_CONFLICT} se l'iscrizione è chiusa,
     *                              {@code FORM_TRANSITION} se è già inviata, {@code INVALID_INPUT} per risposte non valide
     */
    public FormDTO save(CallerContext caller, String enrollmentId, Map<String, String> answers) {
        return transactions.retryOnConflict(() -> saveOnce(caller, enrollmentId, answers));
    }

    /** Invia la scheda: obbligatorie risposte, adempimento soddisfatto, iscrizione completata se non resta altro. */
    public FormDTO submit(CallerContext caller, String enrollmentId) {
        return transactions.retryOnConflict(() -> submitOnce(caller, enrollmentId));
    }

    /** La segreteria riapre una scheda inviata con un motivo: il partecipante la corregge e la reinvia (FR-051). */
    public FormDTO reopen(CallerContext caller, String enrollmentId, String reason) {
        return transactions.retryOnConflict(() -> reopenOnce(caller, enrollmentId, reason));
    }

    // ---------- scritture ----------

    private FormDTO saveOnce(CallerContext caller, String enrollmentId, Map<String, String> answers) {
        Enrollment enrollment = writableByParticipant(caller, enrollmentId);
        FormSubmission submission = existingOrDraft(enrollment);
        if (submission.getStatus() == FormStatus.SUBMITTED) {
            throw new BuonaCacciaException(ErrorCode.FORM_TRANSITION, "La scheda è già stata inviata.");
        }
        var errors = FormRules.validateAnswers(FormRules.questionsOf(submission.getSections()), answers);
        throwIfInvalid(errors);
        Instant now = clock.instant();
        submission.setAnswers(cleaned(answers));
        submission.setUpdatedAt(now);
        enrollment.setUpdatedAt(now);
        persist(submission, enrollment, null, null);
        return toDto(enrollment, submission);
    }

    private FormDTO submitOnce(CallerContext caller, String enrollmentId) {
        Enrollment enrollment = writableByParticipant(caller, enrollmentId);
        FormSubmission submission = existingOrDraft(enrollment);
        if (submission.getStatus() == FormStatus.SUBMITTED) {
            throw new BuonaCacciaException(ErrorCode.FORM_TRANSITION, "La scheda è già stata inviata.");
        }
        var questions = FormRules.questionsOf(submission.getSections());
        var errors = new java.util.ArrayList<>(FormRules.validateAnswers(questions, submission.getAnswers()));
        errors.addAll(FormRules.missingRequired(questions, submission.getAnswers()));
        throwIfInvalid(errors);
        Instant now = clock.instant();
        submission.setStatus(FormStatus.SUBMITTED);
        submission.setSubmittedAt(now);
        submission.setUpdatedAt(now);
        EnrollmentObligation obligation = obligationOf(enrollment);
        if (obligation.getStatus() == ObligationStatus.TODO) {
            obligation.setStatus(ObligationStatus.SATISFIED);
            obligation.setSatisfiedBy(caller.getSubject());
            obligation.setSatisfiedAt(now);
            obligation.setNote(null);
        }
        DomainEventType completed = EnrollmentService.completeIfDone(enrollment, now);
        persist(submission, enrollment, completed, null);
        log.info("Iscrizione {}: scheda inviata", enrollment.getNumber());
        return toDto(enrollment, submission);
    }

    private FormDTO reopenOnce(CallerContext caller, String enrollmentId, String reason) {
        Enrollment enrollment = find(enrollmentId);
        obligationOf(enrollment);
        eventAccessPolicy.checkCanManage(caller, enrollment.getEventLevel(), enrollment.getEventRegion());
        if (!enrollment.isActive()) {
            throw new BuonaCacciaException(ErrorCode.ENROLLMENT_STATE_CONFLICT, "L'iscrizione non è attiva.");
        }
        FormSubmission submission = mongo(() -> submissionRepository.findByEnrollmentId(enrollmentId))
                .filter(s -> s.getStatus() == FormStatus.SUBMITTED)
                .orElseThrow(() -> new BuonaCacciaException(ErrorCode.FORM_TRANSITION, "La scheda non è stata inviata: non c'è nulla da riaprire."));
        Instant now = clock.instant();
        submission.setStatus(FormStatus.DRAFT);
        submission.setSubmittedAt(null);
        submission.setReopenedBy(caller.getSubject());
        submission.setReopenedAt(now);
        submission.setReopenReason(reason);
        submission.setUpdatedAt(now);
        EnrollmentObligation obligation = obligationOf(enrollment);
        obligation.setStatus(ObligationStatus.TODO);
        obligation.setSatisfiedBy(null);
        obligation.setSatisfiedAt(null);
        obligation.setNote(reason);
        if (enrollment.getStatus() == EnrollmentStatus.COMPLETED) {
            enrollment.setStatus(EnrollmentStatus.REGISTERED);
            enrollment.setCompletedAt(null);
            enrollment.setDeadline(ObligationPlanner.deadlineFrom(now));
        } else if (enrollment.getDeadline() != null && enrollment.getDeadline().isBefore(now.plus(MIN_TIME_AFTER_REOPEN))) {
            enrollment.setDeadline(now.plus(MIN_TIME_AFTER_REOPEN));
        }
        enrollment.setUpdatedAt(now);
        persist(submission, enrollment, null, reason);
        log.info("Iscrizione {}: scheda riaperta da {}", enrollment.getNumber(), caller.getSubject());
        return toDto(enrollment, submission);
    }

    /** Scheda e iscrizione insieme, con l'eventuale evento di dominio (completamento o riapertura). */
    private void persist(FormSubmission submission, Enrollment enrollment, DomainEventType completed, String reopenReason) {
        Event event = mongo(() -> eventRepository.findById(enrollment.getEventId())).orElse(null);
        transactions.run(() -> {
            submissionRepository.save(submission);
            Enrollment saved = enrollmentRepository.save(enrollment);
            if (event != null && completed != null) {
                domainEvents.enrollment(completed, saved, event);
            }
            if (event != null && reopenReason != null) {
                domainEvents.formReopened(saved, event, reopenReason);
            }
            return saved;
        });
    }

    // ---------- accesso e controlli ----------

    private Enrollment writableByParticipant(CallerContext caller, String enrollmentId) {
        Enrollment enrollment = find(enrollmentId);
        obligationOf(enrollment);
        if (!isParticipant(caller, enrollment)) {
            if (canManage(caller, enrollment) && mongo(() -> submissionRepository.findByEnrollmentId(enrollmentId))
                    .filter(s -> s.getStatus() == FormStatus.SUBMITTED).isPresent()) {
                throw new BuonaCacciaException(ErrorCode.FORBIDDEN, "La scheda la compila il partecipante.");
            }
            throw notFound();
        }
        if (!enrollment.isActive()) {
            throw new BuonaCacciaException(ErrorCode.ENROLLMENT_STATE_CONFLICT, "L'iscrizione non è attiva: la scheda non si compila più.");
        }
        return enrollment;
    }

    private FormSubmission existingOrDraft(Enrollment enrollment) {
        return mongo(() -> submissionRepository.findByEnrollmentId(enrollment.getId())).orElseGet(() -> draftOf(enrollment));
    }

    /** Scheda nuova con la copia del modello di oggi; non è salvata finché il partecipante non scrive (FR-004). */
    private FormSubmission draftOf(Enrollment enrollment) {
        Event event = mongo(() -> eventRepository.findById(enrollment.getEventId())).orElseThrow(() -> notFound());
        // Le domande dell'evento, se la segreteria le ha scritte; altrimenti il modello del tipo.
        boolean own = event.getFormSections() != null && !FormRules.questionsOf(event.getFormSections()).isEmpty();
        var template = own ? null : templateService.get(event.getTypeCode());
        return FormSubmission.builder()
                .id(java.util.UUID.randomUUID().toString())
                .enrollmentId(enrollment.getId())
                .eventId(enrollment.getEventId())
                .eventLevel(enrollment.getEventLevel())
                .eventRegion(enrollment.getEventRegion())
                .participantId(enrollment.getParticipantId())
                .typeCode(event.getTypeCode())
                .sections(own ? event.getFormSections() : template.getSections())
                .answers(new HashMap<>())
                .status(FormStatus.DRAFT)
                .build();
    }

    private String typeCodeOf(Enrollment enrollment) {
        return mongo(() -> eventRepository.findById(enrollment.getEventId()))
                .map(Event::getTypeCode)
                .orElseThrow(() -> notFound());
    }

    private boolean isParticipant(CallerContext caller, Enrollment enrollment) {
        return caller.getSubject() != null && caller.getSubject().equals(enrollment.getParticipantId());
    }

    private boolean canManage(CallerContext caller, Enrollment enrollment) {
        return eventAccessPolicy.canManage(caller, enrollment.getEventLevel(), enrollment.getEventRegion());
    }

    private static EnrollmentObligation obligationOf(Enrollment enrollment) {
        return enrollment.getObligations().stream()
                .filter(o -> o.getType() == ObligationType.ELECTRONIC_FORM)
                .findFirst()
                .orElseThrow(FormService::notFound);
    }

    private static Map<String, String> cleaned(Map<String, String> answers) {
        Map<String, String> result = new HashMap<>();
        answers.forEach((id, value) -> {
            if (value != null && !value.isBlank()) {
                result.put(id, value.trim());
            }
        });
        return result;
    }

    private static void throwIfInvalid(List<it.buonacaccia.v1.dto.FieldErrorDTO> errors) {
        if (!errors.isEmpty()) {
            throw new BuonaCacciaException(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.defaultMessage(), errors);
        }
    }

    private static FormDTO toDto(Enrollment enrollment, FormSubmission submission) {
        return FormDTO.builder()
                .enrollmentId(enrollment.getId())
                .typeCode(submission.getTypeCode())
                .status(submission.getStatus())
                .sections(FormMapper.INSTANCE.toSectionDtos(submission.getSections()))
                .answers(submission.getAnswers() == null ? Map.of() : submission.getAnswers())
                .submittedAt(submission.getSubmittedAt())
                .reopenedAt(submission.getReopenedAt())
                .reopenReason(submission.getReopenReason())
                .build();
    }

    private Enrollment find(String id) {
        return mongo(() -> enrollmentRepository.findById(id)).orElseThrow(FormService::notFound);
    }

    private <T> T mongo(Supplier<T> operation) {
        return circuitBreakerService.execute(CircuitBreakerService.MONGODB, operation);
    }

    private static BuonaCacciaException notFound() {
        return new BuonaCacciaException(ErrorCode.NOT_FOUND, "Scheda non trovata.");
    }
}
