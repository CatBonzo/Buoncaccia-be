package it.buonacaccia.mock;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

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
 * Tipi evento di prova del profilo {@code mocked} (research R8): crea quelli che mancano, senza
 * toccare quelli già presenti. Fuori dal mock i tipi li crea l'amministrazione con le API.
 */
@Slf4j
@Component
@Profile("mocked")
@RequiredArgsConstructor
public class MockEventTypesLoader implements ApplicationRunner {

    private final EventTypeRepository eventTypeRepository;
    private final Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        Instant now = clock.instant();
        List<EventType> missing = mockTypes(now).stream()
                .filter(type -> !eventTypeRepository.existsById(type.getCode()))
                .toList();
        eventTypeRepository.saveAll(missing);
        log.info("Tipi evento di prova: {} creati, {} già presenti", missing.size(), mockTypes(now).size() - missing.size());
    }

    private static List<EventType> mockTypes(Instant now) {
        return List.of(
                EventType.builder()
                        .code("CFM_EG")
                        .name("Campo di formazione metodologica E/G")
                        .category(EventCategory.LEADER_TRAINING)
                        .defaultRequirements(Requirements.builder()
                                .minAge(21).branches(List.of(Branch.CO)).memberTypes(List.of(MemberType.LEADER))
                                .oneActivePerType(true).build())
                        .defaultObligations(List.of(
                                obligation(ObligationType.GROUP_LEADER_AUTHORIZATION, "Autorizzazione del capo gruppo"),
                                obligation(ObligationType.PAYMENT, "Quota di partecipazione")))
                        .application(false).active(true).createdAt(now).updatedAt(now)
                        .build(),
                EventType.builder()
                        .code("ROSS")
                        .name("Route di orientamento al servizio")
                        .category(EventCategory.RS)
                        .defaultRequirements(Requirements.builder()
                                .minAge(16).maxAge(21).branches(List.of(Branch.RS)).memberTypes(List.of(MemberType.YOUTH))
                                .oneActivePerType(true).maxPerGroup(4).build())
                        .defaultObligations(List.of(
                                obligation(ObligationType.UNIT_LEADER_AUTHORIZATION, "Autorizzazione del capo clan"),
                                obligation(ObligationType.DOCUMENT, "Scheda sanitaria, PDF ≤ 1 MB"),
                                obligation(ObligationType.PAYMENT, "Quota di partecipazione")))
                        .application(false).active(true).createdAt(now).updatedAt(now)
                        .build(),
                EventType.builder()
                        .code("CFA")
                        .name("Campo di formazione associativa")
                        .category(EventCategory.LEADER_TRAINING)
                        .defaultRequirements(Requirements.builder()
                                .minAge(21).branches(List.of(Branch.CO)).memberTypes(List.of(MemberType.LEADER))
                                .oneActivePerType(true).build())
                        .defaultObligations(List.of(
                                obligation(ObligationType.LEADER_VALIDATION, "Convalida della comunità capi"),
                                obligation(ObligationType.PAYMENT, "Quota di partecipazione")))
                        .application(true).active(true).createdAt(now).updatedAt(now)
                        .build());
    }

    private static Obligation obligation(ObligationType type, String description) {
        return Obligation.builder().type(type).description(description).build();
    }
}
