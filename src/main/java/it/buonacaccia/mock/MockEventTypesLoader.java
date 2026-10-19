package it.buonacaccia.mock;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import it.buonacaccia.model.Branch;
import it.buonacaccia.model.EventCategory;
import it.buonacaccia.model.EventType;
import it.buonacaccia.model.MemberType;
import it.buonacaccia.model.Obligation;
import it.buonacaccia.model.ObligationType;
import it.buonacaccia.model.Requirements;
import it.buonacaccia.repository.EventTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Tipi evento del profilo {@code mocked}: i 22 tipi che possono uscire (codice - titolo, elenco di
 * Mahima del 19/10/2026). Crea quelli che mancano; per quelli già presenti aggiorna solo titolo e
 * categoria (requisiti e adempimenti restano quelli salvati) e li riattiva. I tipi dell'elenco
 * precedente che non ci sono più si disattivano, non si cancellano (FR-003). Fuori dal mock i tipi
 * li gestisce l'amministrazione con le API.
 */
@Slf4j
@Component
@Profile("mocked")
@RequiredArgsConstructor
public class MockEventTypesLoader implements ApplicationRunner {

    /** Codici del vecchio elenco di prova, ritirati: gli eventi che li usano restano leggibili. */
    private static final List<String> RETIRED_CODES = List.of("SPEC_EG");

    private final EventTypeRepository eventTypeRepository;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        Instant now = clock.instant();
        List<EventType> wanted = mockTypes(now);
        List<EventType> toSave = new ArrayList<>();
        int created = 0;
        for (EventType type : wanted) {
            Optional<EventType> existing = eventTypeRepository.findById(type.getCode());
            if (existing.isEmpty()) {
                toSave.add(type);
                created++;
            } else if (needsUpdate(existing.get(), type)) {
                EventType stored = existing.get();
                stored.setName(type.getName());
                stored.setCategory(type.getCategory());
                stored.setActive(true);
                stored.setUpdatedAt(now);
                toSave.add(stored);
            }
        }
        for (String code : RETIRED_CODES) {
            eventTypeRepository.findById(code).filter(EventType::isActive).ifPresent(type -> {
                type.setActive(false);
                type.setUpdatedAt(now);
                toSave.add(type);
            });
        }
        eventTypeRepository.saveAll(toSave);
        log.info("Tipi evento di prova: {} creati, {} aggiornati o ritirati, {} in tutto", created, toSave.size() - created, wanted.size());
    }

    private static boolean needsUpdate(EventType stored, EventType wanted) {
        return !wanted.getName().equals(stored.getName()) || wanted.getCategory() != stored.getCategory() || !stored.isActive();
    }

    // Requisiti e adempimenti sono una prima proposta per categoria (da confermare con Mahima): ROSS e Partenti sono
    // uguali, i campi e i momenti di formazione per capi seguono la formazione metodologica, le assemblee e gli altri
    // eventi non hanno limiti di età o di branca.
    private static List<EventType> mockTypes(Instant now) {
        return List.of(
                youth("PO", "Piccole Orme", EventCategory.LC, Branch.LC, 8, 11, ObligationType.UNIT_LEADER_AUTHORIZATION, "Autorizzazione del capo unità", now),
                youth("CAPISQ", "Capi Squadriglia", EventCategory.EG, Branch.EG, 12, 16, ObligationType.LEADER_VALIDATION, "Validazione del capo reparto", now),
                ross("ROSS", "ROSS", now),
                ross("PART", "Partenti", now),
                open("EPPPI", "EPPPI", EventCategory.ASSOCIATION, now),
                leader("CFT", "CFT", EventCategory.LEADER_TRAINING, false, now),
                leader("CFM_LC", "CFM L/C", EventCategory.LEADER_TRAINING, false, now),
                leader("CFM_EG", "CFM E/G", EventCategory.LEADER_TRAINING, false, now),
                leader("CFM_RS", "CFM R/S", EventCategory.LEADER_TRAINING, false, now),
                leader("CFA", "CFA", EventCategory.LEADER_TRAINING, true, now),
                leader("CCG", "CCG", EventCategory.LEADER_TRAINING, false, now),
                leader("MF_PT", "MF-Percorso di tirocinio", EventCategory.LEADER_TRAINING, false, now),
                leader("MF_AM_LC", "MF-Aggiornam. metodologico L/C", EventCategory.LEADER_TRAINING, false, now),
                leader("MF_AM_RS", "MF-Aggiornam. metodologico R/S", EventCategory.LEADER_TRAINING, false, now),
                leader("MF_AM_EG", "MF-Aggiornam. metodologico E/G", EventCategory.LEADER_TRAINING, false, now),
                leader("MF_INTER", "MF-Interbranca", EventCategory.LEADER_TRAINING, false, now),
                leader("MF_CT", "MF-Competenze tecniche", EventCategory.LEADER_TRAINING, false, now),
                open("ASSREG", "Assemblea regionale", EventCategory.ASSOCIATION, now),
                open("ASSZONA", "Assemblea di zona", EventCategory.ASSOCIATION, now),
                open("ALTROREG", "Altro regionale", EventCategory.ASSOCIATION, now),
                leader("MF_CG", "MF-Capi Gruppo", EventCategory.LEADER_TRAINING, false, now),
                leader("MF_FS", "MF-Fede/Spiritualità", EventCategory.LEADER_TRAINING, false, now));
    }

    /** Evento per ragazzi di una branca: serve l'autorizzazione o la convalida del capo e la quota. */
    private static EventType youth(String code, String name, EventCategory category, Branch branch, int minAge, int maxAge,
                                   ObligationType leaderStep, String leaderText, Instant now) {
        return EventType.builder()
                .code(code).name(name).category(category)
                .defaultRequirements(Requirements.builder()
                        .minAge(minAge).maxAge(maxAge).branches(List.of(branch)).memberTypes(List.of(MemberType.YOUTH))
                        .maxPerGroup(6).build())
                .defaultObligations(List.of(obligation(leaderStep, leaderText), obligation(ObligationType.PAYMENT, "Quota di partecipazione")))
                .application(false).active(true).createdAt(now).updatedAt(now)
                .build();
    }

    /** ROSS e Partenti: route per rover e scolte, con scheda sanitaria. */
    private static EventType ross(String code, String name, Instant now) {
        return EventType.builder()
                .code(code).name(name).category(EventCategory.RS)
                .defaultRequirements(Requirements.builder()
                        .minAge(16).maxAge(21).branches(List.of(Branch.RS)).memberTypes(List.of(MemberType.YOUTH))
                        .oneActivePerType(true).maxPerGroup(4).build())
                .defaultObligations(List.of(
                        obligation(ObligationType.UNIT_LEADER_AUTHORIZATION, "Autorizzazione del capo clan"),
                        obligation(ObligationType.DOCUMENT, "Scheda sanitaria, PDF ≤ 1 MB"),
                        obligation(ObligationType.PAYMENT, "Quota di partecipazione")))
                .application(false).active(true).createdAt(now).updatedAt(now)
                .build();
    }

    /** Formazione per capi: dai 21 anni, autorizzazione del capo gruppo e quota. */
    private static EventType leader(String code, String name, EventCategory category, boolean application, Instant now) {
        return EventType.builder()
                .code(code).name(name).category(category)
                .defaultRequirements(Requirements.builder()
                        .minAge(21).branches(List.of(Branch.CO)).memberTypes(List.of(MemberType.LEADER))
                        .oneActivePerType(true).build())
                .defaultObligations(List.of(
                        obligation(ObligationType.GROUP_LEADER_AUTHORIZATION, "Autorizzazione del capo gruppo"),
                        obligation(ObligationType.PAYMENT, "Quota di partecipazione")))
                .application(application).active(true).createdAt(now).updatedAt(now)
                .build();
    }

    /** Assemblee e altri eventi: nessun limite di età o di branca, solo la quota. */
    private static EventType open(String code, String name, EventCategory category, Instant now) {
        return EventType.builder()
                .code(code).name(name).category(category)
                .defaultRequirements(Requirements.builder().build())
                .defaultObligations(List.of(obligation(ObligationType.PAYMENT, "Quota di partecipazione")))
                .application(false).active(true).createdAt(now).updatedAt(now)
                .build();
    }

    private static Obligation obligation(ObligationType type, String description) {
        return Obligation.builder().type(type).description(description).build();
    }
}
