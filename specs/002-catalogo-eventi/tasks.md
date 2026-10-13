---
description: "Task della feature 002 catalogo eventi"
---

# Tasks: Catalogo eventi

**Input**: documenti di progetto in `specs/002-catalogo-eventi/`

**Prerequisiti**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/catalog.openapi.yaml](contracts/catalog.openapi.yaml), [quickstart.md](quickstart.md)

**Test**: obbligatori (principio II della costituzione, test-first). In ogni storia i test si scrivono per primi e devono fallire prima del codice.

**Organizzazione**: per storia utente, ognuna provabile da sola.

## Formato: `[ID] [P?] [Storia] Descrizione`

- **[P]**: si può fare in parallelo (file diversi, nessuna dipendenza aperta)
- **[Storia]**: storia utente della spec (US1…US5)
- Percorsi: `src/main/java/it/buonacaccia/` = `main/`, `src/test/java/it/buonacaccia/` = `test/`

---

## Phase 1: Setup

**Scopo**: preparare dati di prova e configurazione comuni.

- [x] T001 Aggiornare `src/main/resources/mocked-identity.yml`: la segreteria user-002 passa a `isMemberOf: agesci:PIE:segreteria`; aggiungere user-005 `segreteria-lig` ("Segreteria Liguria", agesciUUID `7c1e2a2e-0005-4a6b-9a01-000000000005`, `isMemberOf: agesci:LIG:segreteria`, ruolo `agesci:regione:segreteria`, entitlement `evento:gestione`, `iscrizione:gestione`, `pagamento:registrazione`) (R2)
- [x] T002 [P] Creare `main/config/ClockConfig.java` con un bean `Clock` (`Clock.systemUTC()`) e la costante `ZoneId ROME = Europe/Rome` usata dalle regole di dominio (R1)
- [x] T003 [P] Aggiornare i test esistenti che contano 4 utenti mock (`test/v1/controller/MockAuthControllerTest.java`, `test/mock/MockIdentityServiceTest.java`) a 5 utenti

---

## Phase 2: Foundational (blocca tutte le storie)

**Scopo**: enum, documenti, errori, sicurezza, guardia territoriale.

- [x] T004 [P] Creare `main/model/Region.java`: enum con sigla e nome `ABR, BAS, CAL, CAM, EMR, FVG, LAZ, LIG, LOM, MAR, MOL, PIE, PUG, SAR, SIC, TOS, TAA, UMB, VDA, VEN` e `static Optional<Region> fromCode(String)` (R3)
- [x] T005 [P] Creare gli enum in `main/model/`: `EventCategory` (`LC`, `EG`, `RS`, `LEADER_TRAINING`, `ASSOCIATION`), `EventLevel` (`NATIONAL`, `REGIONAL`, `ZONE`), `EventLifecycle` (`DRAFT`, `PUBLISHED`, `CANCELLED`), `EventStatus` (`DRAFT`, `PUBLISHED`, `REGISTRATION_OPEN`, `REGISTRATION_CLOSED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`), `Availability` (`NOT_YET_OPEN`, `OPEN`, `LIMITED`, `HEAVY_OVERBOOKING`, `CLOSED`), `EnrollmentMode` (`SELF`, `BY_LEADER`, `BOTH`), `ObligationType` (`LEADER_VALIDATION`, `UNIT_LEADER_AUTHORIZATION`, `GROUP_LEADER_AUTHORIZATION`, `PAYMENT`, `DOCUMENT`, `ELECTRONIC_FORM`)
- [x] T006 [P] Creare i sotto-documenti Lombok (`@AllArgsConstructor @NoArgsConstructor @Getter @Setter @ToString @Builder`) in `main/model/`: `Requirements` (`minAge`, `maxAge`, `branches` lista `Branch`, `memberTypes` lista `MemberType`, `oneActivePerType`, `maxPerGroup`), `Obligation` (`type`, `description`), `Contact` (`name`, `email`, `phone`), `Contacts` (`methodological`, `administrative`)
- [x] T007 [P] Creare `main/model/EventType.java` (`@Document("event_types")`, `@Id code`, `name`, `category`, `defaultRequirements`, `defaultObligations`, `application` default false, `active` default true, `createdAt`, `updatedAt`)
- [x] T008 [P] Creare `main/model/Event.java` (`@Document("events")`) con tutti i campi di data-model.md: `id`, `code` (`@Indexed(unique = true)`), `typeCode`, `title`, `description`, `level`, `region`, `zone`, `location`, `province`, `startDate`, `endDate`, `registrationOpenDate`, `registrationCloseDate`, `seats`, `fee` (`BigDecimal`, `@Field(targetType = DECIMAL128)`), `enrollmentMode`, `requirements`, `obligations`, `contacts`, `lifecycle`, `registrationClosedEarlyAt`, `cancellationReason`, `cancelledAt`, `registrationOpensAt`, `registrationClosesAt`, `startsAt`, `endsAt`, `enrolledCount` (0), `@Version Long version`, `createdBy`, `createdAt`, `updatedAt`; indici composti `{lifecycle, startsAt}`, `{region, startsAt}`, `{typeCode, startsAt}` con `@CompoundIndex`
- [x] T009 [P] Creare `main/model/Counter.java` (`@Document("counters")`, `@Id id`, `long seq`)
- [x] T010 [P] Creare `main/repository/EventTypeRepository.java` e `main/repository/EventRepository.java` (`MongoRepository`); abilitare la creazione automatica degli indici (`spring.data.mongodb.auto-index-creation: true` in `application.yml`)
- [x] T011 Aggiungere a `main/config/error/ErrorCode.java` i codici di dominio `CATALOG`: `STATE_CONFLICT` 40901 (409), `EVENT_TYPE_EXISTS` 40902 (409), `STALE_VERSION` 40903 (409), `FIELD_LOCKED` 40904 (409), `PUBLISH_INVARIANTS` 42201 (422) (R7)
- [x] T012 Creare `main/v1/dto/FieldErrorDTO.java` (`field`, `message`) e far portare a `BuonaCacciaException` un `payload` facoltativo; in `GlobalExceptionHandler` mettere l'elenco dei `FieldErrorDTO` nel `payload` per `MethodArgumentNotValidException` (42200) e per le eccezioni con payload, e tradurre `OptimisticLockingFailureException` in 409 `40903` (R4, R7)
- [x] T013 Aggiornare `main/config/SecurityConfig.java`: `GET /api/v1/regions`, `GET /api/v1/event-types/**`, `GET /api/v1/events` e `GET /api/v1/events/{id}` pubblici; `GET /api/v1/events/managed` e tutti i metodi non GET autenticati (R9)
- [x] T014 Creare `main/service/guard/CallerContext.java`: dal `Jwt` corrente ricava `sub`, ruoli, entitlement e regione da `isMemberOf` (stringa o lista; valore `agesci:<SIGLA>:segreteria` con sigla in `Region`); `isAdmin()` = ruolo `agesci:sistema:amministratore`; vuoto se non autenticato (R2)
- [x] T015 Scrivere `test/service/guard/EventAccessPolicyTest.java` (unitario, deve fallire): admin gestisce NATIONAL/REGIONAL/ZONE di ogni regione; segreteria PIE gestisce REGIONAL/ZONE PIE; segreteria PIE → 403 su LIG e su NATIONAL; regione mancante o sconosciuta (`agesci:XYZ:segreteria`, `agesci:socio:false`) → 403; `isMemberOf` come lista
- [x] T016 Creare `main/service/guard/EventAccessPolicy.java`: `checkCanManage(CallerContext, EventLevel, Region)` lancia 403 `40300`; `canManage(...)` booleano per la visibilità delle bozze (FR-010, Q11)
- [x] T017 Scrivere `test/service/EventStatusResolverTest.java` (unitario, deve fallire): tabella degli stati di data-model.md con `Clock.fixed`; apertura esatta alle 09:00 Europe/Rome (08:59:59 → PUBLISHED, 09:00 → REGISTRATION_OPEN); chiusura alle 23:59:59; giorni del cambio dell'ora legale (29/03/2026 e 25/10/2026); chiusura anticipata; soglie 89%/90%/120%/121% dell'indicatore
- [x] T018 Creare `main/service/EventStatusResolver.java`: `computeInstants(Event)` (apertura 09:00, chiusura 23:59:59.999 del giorno di chiusura, inizio 00:00 del giorno di partenza, fine 23:59:59.999 del giorno di rientro, tutto in Europe/Rome → `Instant`), `status(Event, Instant now)`, `availability(Event, Instant now)` (FR-013, FR-014, FR-021, R1)

**Checkpoint**: base pronta, T015 e T017 verdi.

---

## Phase 3: User Story 1 - Creare e pubblicare un evento della propria regione (P1) 🎯 MVP

**Obiettivo**: la segreteria crea una bozza della propria regione, la completa e la pubblica; gli errori elencano tutti i campi.

**Test indipendente**: con il token di `segreteria` (PIE) `POST /api/v1/events` crea una bozza `PIE-2027-001`, `POST /events/{id}/publish` la pubblica; con un dato mancante risponde 422 `42201` con l'elenco; `segreteria-lig` riceve 403 sullo stesso evento.

### Test per US1 (prima del codice)

- [x] T019 [US1] Scrivere `test/v1/controller/EventManagementTest.java` (integrazione, `AbstractTest`, token mock): creazione bozza con default di requisiti e adempimenti dal tipo (FR-005); codice `PIE-2027-001` e progressivo `PIE-2027-002`; bozza salvabile incompleta (FR-008); tipo disattivato → 422 alla creazione, ma una bozza il cui tipo viene disattivato dopo si pubblica lo stesso (caso limite); senza token → 401; senza `evento:gestione` → 403; segreteria PIE che crea per LIG o NATIONAL → 403; pubblicazione valida → stato secondo l'ora; pubblicazione con titolo vuoto, date incoerenti e posti 0 → 422 `42201` con tre voci nel `payload`; `segreteria-lig` che pubblica un evento PIE → 403; ripubblicare → 409 `40901`; `GET /events/managed` per admin (tutto) e segreteria (solo PIE, bozze comprese)

### Implementazione per US1

- [x] T020 [P] [US1] Creare i DTO in `main/v1/dto/`: `EventRequestDTO` (campi modificabili, con Bean Validation: `title` max 200, `description` max 5000, `zone` max 100, `province` `^[A-Z]{2}$`, `seats` > 0, `fee` ≥ 0 con due decimali, `typeCode` e `level` obbligatori, `version` per il PUT), `EventDTO` (dati pubblici + `status`, `availability`, `enrolledCount`, `version`, `cancellationReason`), `VersionDTO` (`version` obbligatorio), `RequirementsDTO`, `ObligationDTO`, `ContactsDTO`, `ContactDTO` come classi Lombok
- [x] T021 [P] [US1] Creare `main/v1/mapper/EventMapper.java` (MapStruct, `INSTANCE`): `toEntity(EventRequestDTO)`, `update(EventRequestDTO, @MappingTarget Event)`, `toDto(Event)` con `status` e `availability` passati dal service
- [x] T022 [P] [US1] Creare `main/service/EventCodeGenerator.java`: `next(Region|null, int anno)` con `MongoTemplate.findAndModify` su `counters` (`$inc seq`, upsert, `returnNew`), chiave `PIE-2027` o `NAZ-2027`, formato `%s-%d-%03d` (R5)
- [x] T023 [US1] Creare `main/service/EventValidator.java`: `validateForPublish(Event, EventType)` raccoglie TUTTI gli errori (titolo, località, date presenti; tipo esistente, anche se disattivato dopo la creazione della bozza; partenza ≤ rientro; apertura < chiusura ≤ partenza; posti > 0; quota ≥ 0; regione obbligatoria salvo NATIONAL; zona obbligatoria se e solo se ZONE; `minAge` ≤ `maxAge`) e lancia 422 `42201` con l'elenco (FR-007)
- [x] T024 [US1] Creare `main/service/EventService.java` con `create`, `publish`, `findManaged`, `findById`: controllo `EventAccessPolicy` prima di ogni scrittura, tipo attivo, default dal tipo, `computeInstants` a ogni salvataggio, `createdBy` = `sub`, chiamate Mongo dentro `CircuitBreakerService`
- [x] T025 [US1] Creare `main/v1/controller/EventController.java` con `POST /api/v1/events`, `POST /api/v1/events/{id}/publish`, `GET /api/v1/events/managed` (`@SecurityAccess(entitlement = EVENTO_GESTIONE)`), risposte nell'envelope, annotazioni OpenAPI come nel contratto

**Checkpoint**: T019 verde; US1 provabile con quickstart scenari 1–4.

---

## Phase 4: User Story 2 - Cercare eventi e vederne il dettaglio senza login (P1)

**Obiettivo**: catalogo pubblico con filtri, ordinamento, paginazione e dettaglio con indicatore.

**Test indipendente**: senza token `GET /api/v1/events?region=PIE&sort=startDate,asc` restituisce solo eventi pubblicati PIE; le bozze non compaiono né nella ricerca né nel dettaglio (404).

### Test per US2 (prima del codice)

- [x] T026 [US2] Scrivere `test/v1/controller/EventCatalogTest.java`: ricerca senza token; filtri `region`, `category`, `type`, `availability`, `status`, `startFrom`/`startTo`, `q` (case-insensitive, caratteri speciali escapati); bozze e annullati esclusi salvo `status=CANCELLED`; ordinamento `startDate`, `title`, `type`; pagina `content/page/size/totalElements/totalPages`; `size` 101, pagina negativa, ordinamento sconosciuto e data non valida → 422 con il parametro nel `payload`; ricerca senza risultati e pagina oltre l'ultima → 200 con elenco vuoto e totale reale; dettaglio pubblicato e annullato visibili; dettaglio bozza → 404 senza token e per la segreteria LIG, 200 per la segreteria PIE; id inesistente → 404; token non valido → 401
- [x] T027 [P] [US2] Creare `test/v1/controller/RegionControllerTest.java`: `GET /api/v1/regions` pubblico, 20 regioni con `code` e `name`

### Implementazione per US2

- [x] T028 [P] [US2] Creare `main/v1/dto/RegionDTO.java` e `main/v1/controller/RegionController.java` (`GET /api/v1/regions`) (FR-022)
- [x] T029 [P] [US2] Creare `main/v1/dto/EventPageDTO.java` (`content`, `page`, `size`, `totalElements`, `totalPages`) e `main/v1/dto/EventSearchCriteria.java` (filtri della query)
- [x] T030 [US2] Creare `main/repository/EventSearchRepository.java` con `MongoTemplate`: `Criteria` dai filtri, stato tradotto in condizioni sugli istanti all'ora corrente, disponibilità tradotta in condizioni su stato e `enrolledCount/seats`, categoria tradotta nei codici tipo della categoria, `q` con `Pattern.quote` case-insensitive, `Pageable` con `size` ≤ 100 (R6)
- [x] T031 [US2] Aggiungere a `EventService` `search(...)` e `getPublicDetail(id, CallerContext)` (bozza visibile solo se `EventAccessPolicy.canManage`, altrimenti 404) e a `EventController` `GET /api/v1/events` e `GET /api/v1/events/{id}` pubblici (FR-019, FR-020, R9)

**Checkpoint**: T026 e T027 verdi; quickstart scenari 5–8.

---

## Phase 5: User Story 3 - Apertura e chiusura delle iscrizioni (P2)

**Obiettivo**: lo stato segue l'ora italiana senza job; la segreteria può chiudere in anticipo.

**Test indipendente**: con un `Clock` fisso spostato prima e dopo le 09:00 del giorno di apertura il dettaglio passa da PUBLISHED a REGISTRATION_OPEN; `close-registrations` porta a REGISTRATION_CLOSED.

### Test per US3 (prima del codice)

- [x] T032 [US3] Scrivere `test/v1/controller/EventScheduleTest.java` con un `Clock` sostituibile (`@TestConfiguration` con un clock mutabile `@Primary`): dettaglio e ricerca cambiano stato a 08:59:59/09:00 del giorno di apertura, dopo le 23:59:59 della chiusura, all'inizio della partenza e dopo il rientro; chiusura anticipata da REGISTRATION_OPEN → REGISTRATION_CLOSED; chiusura anticipata da PUBLISHED o da REGISTRATION_CLOSED → 409 `40901`; segreteria LIG → 403

### Implementazione per US3

- [x] T033 [US3] Usare il bean `Clock` in `EventService` ed `EventSearchRepository` per l'ora corrente (nessun `Instant.now()` diretto)
- [x] T034 [US3] Aggiungere `closeRegistrations(id, version)` a `EventService` (solo da REGISTRATION_OPEN, valorizza `registrationClosedEarlyAt`) e `POST /api/v1/events/{id}/close-registrations` a `EventController` (FR-015)

**Checkpoint**: T032 verde; quickstart scenari 9–10.

---

## Phase 6: User Story 4 - Gestire i tipi evento (P2)

**Obiettivo**: l'amministrazione crea, modifica e disattiva i tipi; la consultazione dei tipi attivi è pubblica.

**Test indipendente**: con il token `amministratore` `POST /api/v1/event-types` crea `CAMPO_TEST`; un secondo `POST` con lo stesso codice dà 409 `40902`; la segreteria riceve 403; `GET /api/v1/event-types` senza token mostra solo gli attivi.

### Test per US4 (prima del codice)

- [x] T035 [US4] Scrivere `test/v1/controller/EventTypeControllerTest.java`: creazione con codice `^[A-Z0-9_]{2,20}$` e nome max 120 (altrimenti 422 con elenco); duplicato → 409 `40902`; modifica; disattivazione; tipo disattivato escluso dalla lista pubblica e non usabile per nuovi eventi; eventi esistenti invariati; senza `tipoevento:gestione` → 403; tipo inesistente → 404

### Implementazione per US4

- [x] T036 [P] [US4] Creare `main/v1/dto/EventTypeDTO.java` e `main/v1/mapper/EventTypeMapper.java` (MapStruct, `INSTANCE`)
- [x] T037 [US4] Creare `main/service/EventTypeService.java` (`findActive`, `findAll` per i gestori, `get`, `create`, `update`, `deactivate`; nessuna cancellazione) (FR-001–FR-003)
- [x] T038 [US4] Creare `main/v1/controller/EventTypeController.java`: `GET /api/v1/event-types` e `GET /{code}` pubblici; `POST`, `PUT /{code}`, `POST /{code}/deactivate` con `@SecurityAccess(entitlement = TIPOEVENTO_GESTIONE)`
- [x] T039 [P] [US4] Creare `main/mock/MockEventTypesLoader.java` (`ApplicationRunner`, `@Profile("mocked")`): crea se mancano `CFM_EG` (LEADER_TRAINING), `ROSS` (RS), `CFA` (LEADER_TRAINING) con requisiti e adempimenti plausibili (R8)

**Checkpoint**: T035 verde; quickstart scenari 11–12.

---

## Phase 7: User Story 5 - Modificare o annullare un evento pubblicato (P3)

**Obiettivo**: modifiche limitate ai campi consentiti dopo la pubblicazione, annullamento con motivo, conflitti di versione.

**Test indipendente**: dopo la pubblicazione `PUT` del titolo riesce, `PUT` del tipo dà 409 `40904`; due `PUT` con la stessa `version` → la seconda 409 `40903`; `cancel` con motivo → CANCELLED visibile nel dettaglio.

### Test per US5 (prima del codice)

- [x] T040 [US5] Aggiungere a `test/v1/controller/EventManagementTest.java`: in bozza tutto modificabile; dopo la pubblicazione modificabili titolo, descrizione, località, provincia, contatti, posti, quota, date di partenza e rientro, data di chiusura se non passata; bloccati tipo, livello, regione, zona, data di apertura, modalità, requisiti, adempimenti → 409 `40904`; modifica che rompe un invariante → 422 `42201`; modifica IN_PROGRESS/COMPLETED/CANCELLED → 409 `40901`; versione superata → 409 `40903`; annullamento senza motivo → 422; motivo > 500 → 422; annullamento da DRAFT…IN_PROGRESS → CANCELLED con motivo e `cancelledAt`; da COMPLETED → 409 `40901`; segreteria LIG → 403

### Implementazione per US5

- [x] T041 [P] [US5] Creare `main/v1/dto/CancelDTO.java` (`reason` obbligatorio max 500, `version` obbligatorio)
- [x] T042 [US5] Aggiungere a `EventValidator` `checkLockedFields(Event attuale, EventRequestDTO)` che lancia 409 `40904` col nome del primo campo bloccato cambiato (FR-016)
- [x] T043 [US5] Aggiungere a `EventService` `update(id, dto)` (controllo versione esplicito + `@Version`, campi bloccati se non DRAFT, invarianti se pubblicato, ricalcolo istanti) e `cancel(id, CancelDTO)` (FR-016–FR-018)
- [x] T044 [US5] Aggiungere a `EventController` `PUT /api/v1/events/{id}` e `POST /api/v1/events/{id}/cancel`

**Checkpoint**: tutte le storie verdi.

---

## Phase 8: Polish e aspetti trasversali

- [x] T045 [P] Verificare in `test/v1/controller/OpenApiDocsTest.java` che `/api-docs` contenga i 14 percorsi del contratto
- [x] T046 [P] Estendere `test/v1/controller/EnvelopeTest.java` con le risposte di errore del catalogo (401, 403, 404, 409, 422): i quattro campi dell'envelope sempre presenti (SC-006)
- [x] T047 [P] Aggiungere a `test/v1/controller/EventCatalogTest.java` una prova di carico leggera: 1.000 eventi pubblicati, prima pagina filtrata in meno di 1 secondo (SC-004), e una prova con MongoDB fermo → 503 `50300` nell'envelope (caso limite)
- [x] T048 Eseguire `./gradlew build --max-workers=1` e lo scenario completo di [quickstart.md](quickstart.md) con `test-src/env/test.env`
- [x] T049 Aggiornare `AGENTS.md` (decisioni dell'implementazione) e `docs/tesi/diario-capitolo-3.txt` (voce dell'implementazione)

---

## Dipendenze e ordine

- **Setup (Phase 1)**: nessuna dipendenza.
- **Foundational (Phase 2)**: dopo il setup; blocca tutte le storie.
- **US1 (Phase 3)**: dopo la Foundational. È l'MVP.
- **US2 (Phase 4)**: dopo la Foundational; i test creano gli eventi con le API di US1, quindi conviene farla dopo US1.
- **US3 (Phase 5)**: usa creazione e pubblicazione di US1 e il dettaglio di US2.
- **US4 (Phase 6)**: indipendente dagli eventi; il controllo "tipo disattivato non usabile" tocca `EventService` di US1.
- **US5 (Phase 7)**: dopo US1.
- **Polish (Phase 8)**: alla fine.

Ordine consigliato con una persona sola: Setup → Foundational → US1 → US2 → US4 → US3 → US5 → Polish.

### Dentro ogni storia

Test (devono fallire) → DTO e mapper → service → controller → test verdi.

## Parallelismo

- Phase 2: T004–T010 sono file diversi e si possono scrivere insieme; T015 e T017 (test) in parallelo fra loro.
- US1: T020, T021, T022 insieme, poi T023 → T024 → T025.
- US2: T027, T028, T029 insieme mentre si scrive T026.
- US4: T036 e T039 insieme, poi T037 → T038.

```text
# Esempio US1
T020 DTO degli eventi           ┐
T021 EventMapper                ├─ in parallelo
T022 EventCodeGenerator         ┘
T023 EventValidator → T024 EventService → T025 EventController
```

## Strategia di implementazione

1. **MVP**: Setup + Foundational + US1. Una segreteria crea e pubblica un evento della propria regione: è già la regola Q11 provata davanti al relatore.
2. **Incremento 2**: US2, il catalogo pubblico che userà il frontend.
3. **Incremento 3**: US4 e US3, tipi evento e stato legato all'ora.
4. **Incremento 4**: US5, modifiche e annullamento.

Ogni incremento chiude con build verde e un commit; la PR verso `feature` si apre a fine fase.
