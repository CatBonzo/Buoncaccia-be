# Implementation Plan: Catalogo eventi

**Branch**: `002-catalogo-eventi` | **Date**: 2026-10-13 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-catalogo-eventi/spec.md`

## Summary

Il backend aggiunge tre risorse: le regioni (lista fissa), i tipi evento (gestiti dall'amministrazione) e gli eventi. Una segreteria crea un evento in bozza per la propria regione, letta da `isMemberOf` (`agesci:PIE:segreteria`), lo completa e lo pubblica. La pubblicazione controlla tutti gli invarianti e risponde con l'elenco dei campi da correggere. Lo stato dipendente dal tempo (iscrizioni aperte, chiuse, in corso, concluso) non è aggiornato da un job: si calcola a ogni lettura dagli istanti salvati in Europe/Rome, quindi è sempre coerente con l'ora. Il catalogo pubblico offre ricerca con filtri, ordinamento e paginazione, e il dettaglio con l'indicatore di disponibilità. Le modifiche concorrenti sono protette da una versione ottimistica. Dettagli in [research.md](research.md).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.1 (webmvc, security, oauth2-resource-server, data-mongodb, validation), springdoc-openapi 3.1.1, Lombok, MapStruct 1.6.3, Resilience4j 2.4.0: come la feature 001, nessuna dipendenza nuova

**Storage**: MongoDB 8.0, nuove collezioni `event_types`, `events`, `counters`

**Testing**: JUnit 5, Spring Boot Test, Testcontainers (MongoDB); `Clock` sostituibile per i test sul tempo

**Target Platform**: applicazione Spring Boot su PC di sviluppo (Docker Compose), CI su GitHub Actions

**Project Type**: web service (REST API per un frontend separato)

**Performance Goals**: SC-004, prima pagina di ricerca su 1.000 eventi in meno di 1 secondo

**Constraints**: ora italiana (Europe/Rome) per BR-03, anche al cambio dell'ora legale; contratto `/api/v1` senza rotture rispetto alla 001; solo uso locale

**Scale/Scope**: 3 collezioni, 14 endpoint (1 regioni, 5 tipi evento, 8 eventi), 5 utenti di prova

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Verifica | Esito |
| --- | --- | --- |
| I. Qualità del codice | Controller in `v1.controller` senza logica; regole (invarianti, stati, campi bloccati) in `service`; regola territoriale in `service.guard.EventAccessPolicy`; conversioni con MapStruct in `v1.mapper` | ✅ |
| II. Test-first | Test di integrazione per US1–US5 e i casi limite, scritti prima; test unitari per `EventStatusResolver` (cambio dell'ora legale) ed `EventAccessPolicy` | ✅ |
| III. Coerenza con i client | Contratto OpenAPI 3.1 in `contracts/` prima del codice; stesso envelope; status significativo; nuovi codici `SSSNN` nel catalogo; 422 con `payload` = campi da correggere, senza cambiare la forma dell'envelope | ✅ |
| IV. Semplicità | Stato calcolato invece di un job pianificato; regioni come enum; versione ottimistica di Spring Data invece di lock; nessuna nuova infrastruttura | ✅ |
| Vincoli tecnologici | Java 21, Boot 4.1, Gradle Groovy, MongoDB; permessi dagli entitlement del token, regione da `isMemberOf`; Kafka non serve (le notifiche arrivano con la fase 4) | ✅ |

Ricontrollo dopo la Fase 1 (design): ✅ nessuna violazione. Unico ampliamento del contratto comune: per gli errori 422 `payload` può contenere l'elenco dei campi (R7); la forma dell'envelope non cambia e i client della 001 non sono toccati.

## Project Structure

### Documentation (this feature)

```text
specs/002-catalogo-eventi/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── catalog.openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

Solo i file nuovi o toccati; il resto è quello della feature 001.

```text
src/main/java/it/buonacaccia/
├── config/
│   ├── SecurityConfig.java              # + GET pubblici di regions, event-types, events
│   ├── ClockConfig.java                 # bean Clock (Europe/Rome per le regole di dominio)
│   └── error/ErrorCode.java, GlobalExceptionHandler.java   # + codici CATALOG, 422 con elenco campi, versione superata
├── mock/
│   └── MockEventTypesLoader.java        # tipi evento di prova (R8)
├── model/
│   ├── Region.java, EventCategory.java, EventLevel.java, EventLifecycle.java,
│   │   EventStatus.java, Availability.java, EnrollmentMode.java, ObligationType.java
│   ├── EventType.java, Event.java, Counter.java
│   └── Requirements.java, Obligation.java, Contacts.java, Contact.java
├── repository/
│   ├── EventTypeRepository.java, EventRepository.java
│   └── EventSearchRepository.java       # MongoTemplate: filtri, ordinamento, pagina (R6)
├── service/
│   ├── EventTypeService.java
│   ├── EventService.java                # crea, modifica, pubblica, chiude, annulla
│   ├── EventStatusResolver.java         # stato effettivo e disponibilità dal tempo (R1)
│   ├── EventValidator.java              # invarianti di pubblicazione (FR-007), campi bloccati (FR-016)
│   ├── EventCodeGenerator.java          # PIE-2027-001 con counters (R5)
│   └── guard/
│       ├── CallerContext.java           # ruoli e regione letti dal token
│       └── EventAccessPolicy.java       # Q11 (FR-010)
└── v1/
    ├── controller/RegionController.java, EventTypeController.java, EventController.java
    ├── dto/ EventTypeDTO, EventRequestDTO, EventDTO, EventPageDTO, VersionDTO, CancelDTO, RegionDTO, FieldErrorDTO …
    └── mapper/EventTypeMapper.java, EventMapper.java

src/main/resources/mocked-identity.yml   # segreteria → agesci:PIE:segreteria, + segreteria-lig (user-005)

src/test/java/it/buonacaccia/
├── service/EventStatusResolverTest.java, guard/EventAccessPolicyTest.java
└── v1/controller/RegionControllerTest.java, EventTypeControllerTest.java,
    EventManagementTest.java (US1, US5), EventCatalogTest.java (US2), EventScheduleTest.java (US3)
```

**Structure Decision**: stessa applicazione e stessi package della feature 001 (`v1` → `service` → `repository` → `model`). La regola territoriale sta in `service.guard` accanto a quella della guardia, perché dipende dal singolo evento e non si può esprimere nell'annotazione `@SecurityAccess`.

## Complexity Tracking

Nessuna violazione della costituzione da giustificare.
