package it.buonacaccia.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Lavori pianificati delle iscrizioni (research R8 della 003), ogni 5 minuti con l'ora del
 * {@code Clock}: scadenze (FR-012, entro 15 minuti) e lista d'attesa alla partenza (FR-016). Con una
 * sola istanza dell'applicazione non serve un lock distribuito; i test chiamano i metodi direttamente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnrollmentJobs {

    private final EnrollmentService enrollmentService;

    @Scheduled(fixedDelayString = "${buonacaccia.jobs.enrollments-delay:PT5M}",
            initialDelayString = "${buonacaccia.jobs.enrollments-delay:PT5M}")
    public void run() {
        expireOverdue();
        cancelWaitingAtStart();
    }

    public int expireOverdue() {
        return enrollmentService.expireOverdue();
    }

    public int cancelWaitingAtStart() {
        return enrollmentService.cancelWaitingAtStart();
    }
}
