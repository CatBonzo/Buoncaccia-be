# Implementation Plan: Iscrizioni e lista d'attesa

**Branch**: `003-iscrizioni` | **Date**: 2026-10-14 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-iscrizioni/spec.md`

## Summary

Il backend aggiunge l'iscrizione agli eventi pubblicati: un socio R/S o capo si iscrive da solo, un capo unità iscrive un ragazzo della propria unità. Alla registrazione si controllano finestra, censimento, modalità e requisiti BR-06, raccogliendo tutti i motivi di rifiuto. Ordine di arrivo e posizione (nei posti / in attesa) sono assegnati con aggiornamenti atomici su un documento di capienza separato per evento, così restano esatti anche con 200 richieste nello stesso istante. L'iscrizione nasce con gli adempimenti dell'evento e una scadenza a 9 giorni; diventa completa quando sono tutti soddisfatti, scade se non lo sono e libera il posto per il primo in attesa. Il ritiro calcola il diritto al rimborso con BR-10 (7 giorni); l'annullamento dell'evento annulla le iscrizioni. Un job ogni 5 minuti gestisce scadenze e lista d'attesa alla partenza. Il catalogo mostra il numero di iscritti letto dalla capienza. Dettagli in [research.md](research.md).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1 (webmvc, security, oauth2-resource-server, data-mongodb, validation), springdoc-openapi 3.1.1, Lombok, MapStruct 1.6.3, Resilience4j 2.4.0: come le feature 001 e 002, nessuna dipendenza nuova; `@EnableScheduling` per il job

**Storage**: MongoDB 8.0 (replica set); nuove collezioni `enrollments` ed `event_capacity`; campi nuovi su `users` (`birthDate`, `census.groupLeader`); `events.enrolledCount` rimosso

**Testing**: JUnit 5, Spring Boot Test, Testcontainers (MongoDB), `MutableClock` della fase 2; test di concorrenza con 200 thread sul servizio di capienza

**Target Platform**: applicazione Spring Boot su PC di sviluppo (Docker Compose), CI su GitHub Actions

**Project Type**: web service (REST API per un frontend separato)

**Performance Goals**: SC-002, 200 iscrizioni contemporanee allo stesso evento con ordine di arrivo unico e senza buchi; risposta all'iscrizione sotto 1 secondo

**Constraints**: ora italiana per scadenze, età e rimborso; contratto `/api/v1` senza rotture rispetto a 001 e 002 (il campo `enrolledCount` di `EventDTO` resta, ora valorizzato); una sola istanza dell'applicazione; solo uso locale

**Scale/Scope**: 2 collezioni nuove, 10 endpoint nuovi, 1 job, 7 utenti di prova e 1 tipo evento di prova (`SPEC_EG`) in più

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Verifica | Esito |
| --- | --- | --- |
| I. Qualità del codice | Controller in `v1.controller` senza logica; requisiti in `EnrollmentEligibility`, adempimenti in `ObligationPlanner`, posti in `EventCapacityService`, permessi sulla persona in `service.guard.EnrollmentAccessPolicy`; conversioni con MapStruct | ✅ |
| II. Test-first | Test di integrazione per US1–US5 e i casi limite scritti prima; test unitari per idoneità, adempimenti e rimborso; test di concorrenza per SC-002 | ✅ |
| III. Coerenza con i client | Contratto OpenAPI 3.1 in `contracts/` prima del codice; stesso envelope; nuovo dominio `ENROLLMENT` con codici `SSSNN`; 422 con l'elenco dei motivi come i 422 della fase 2; `EventDTO` invariato nella forma | ✅ |
| IV. Semplicità | Aggiornamenti atomici su un documento invece di transazioni; un job `@Scheduled` senza ShedLock (una sola istanza); niente code né eventi asincroni (le notifiche sono della fase 4) | ✅ |
| Vincoli tecnologici | Java 21, Boot 4.1, Gradle Groovy, MongoDB; permessi dagli entitlement del token più la relazione con la persona (AUTH-2); Kafka non serve | ✅ |

Ricontrollo dopo la Fase 1 (design): ✅ nessuna violazione. Due cambi a codice esistente, entrambi compatibili: `Event.enrolledCount` esce dal documento (il DTO lo legge dalla capienza, R2) e il messaggio di `40903` diventa generico (R11).

## Project Structure

### Documentation (this feature)

```text
specs/003-iscrizioni/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── enrollments.openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

Solo i file nuovi o toccati; il resto è quello delle feature 001 e 002.

```text
src/main/java/it/buonacaccia/
├── BuonaCacciaApplication.java          # + @EnableScheduling
├── config/
│   ├── SecurityConfig.java              # le nuove API richiedono il token
│   └── error/ErrorCode.java             # + dominio ENROLLMENT (40910, 40911, 40912, 42210), CATALOG 40905
├── mock/
│   ├── MockedIdentityProperties.java    # + birthDate, groupLeader; anno = corrente se assente
│   ├── MockEventTypesLoader.java        # + SPEC_EG (campo di specialità E/G, solo tramite capo)
│   └── MockIdentityService.java
├── model/
│   ├── User.java (+ birthDate), Census.java (+ groupLeader), Event.java (- enrolledCount)
│   ├── Enrollment.java, EnrollmentObligation.java, EventCapacity.java
│   └── EnrollmentStatus.java, SeatPosition.java, ObligationStatus.java, ClosingReason.java
├── repository/
│   ├── EnrollmentRepository.java
│   ├── EventCapacityRepository.java     # MongoTemplate: reserve, release, promote (R1)
│   └── EventSearchRepository.java       # disponibilità tramite la capienza (R2)
├── service/
│   ├── EnrollmentService.java           # iscrive, ritira, autorizza, soddisfa, annulla per evento
│   ├── EnrollmentEligibility.java       # BR-01, BR-02, BR-06 (R4–R6)
│   ├── ObligationPlanner.java           # adempimenti iniziali e alla promozione (R7)
│   ├── EventCapacityService.java        # posti, promozione, cambio dei posti (R1, R10)
│   ├── EnrollmentJobs.java              # scadenze e attesa alla partenza (R8)
│   ├── EventService.java                # + capienza alla pubblicazione, cambio posti, annullamento
│   └── guard/CallerContext.java (+ entitlement), EnrollmentAccessPolicy.java  # chi vede e chi agisce (FR-021)
└── v1/
    ├── controller/EnrollmentController.java, EventEnrollmentController.java
    ├── dto/ EnrollmentRequestDTO, EnrollmentDTO, EnrollmentSummaryDTO, EnrollmentPageDTO,
    │        EnrollmentObligationDTO, ReasonDTO, NoteDTO
    └── mapper/EnrollmentMapper.java

src/main/resources/mocked-identity.yml   # + esploratore, lupetto, rover, rover-lig, scolta, capogruppo, socio-non-in-regola

src/test/java/it/buonacaccia/
├── service/EnrollmentEligibilityTest.java, ObligationPlannerTest.java, RefundRuleTest.java,
│           EventCapacityConcurrencyTest.java
└── v1/controller/SelfEnrollmentTest.java (US1), LeaderEnrollmentTest.java (US2),
    EnrollmentLifecycleTest.java (US3), WithdrawalTest.java (US4), EnrollmentListsTest.java (US5)
```

**Structure Decision**: stessa applicazione e stessi package. Le API sotto `/api/v1/events/{id}/enrollments` (iscriversi, elenco della segreteria) stanno in `EventEnrollmentController`; quelle sull'iscrizione singola e sugli elenchi personali in `EnrollmentController`. La relazione con la persona (capo unità, capo gruppo, presentatore) sta in `service.guard` accanto a `EventAccessPolicy`, perché non si può esprimere nell'annotazione `@SecurityAccess`.

## Complexity Tracking

Nessuna violazione della costituzione da giustificare.
