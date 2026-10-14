---
description: "Task della feature 003 iscrizioni e lista d'attesa"
---

# Tasks: Iscrizioni e lista d'attesa

**Input**: documenti di progetto in `specs/003-iscrizioni/`

**Prerequisiti**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/enrollments.openapi.yaml](contracts/enrollments.openapi.yaml), [quickstart.md](quickstart.md)

**Test**: obbligatori (principio II della costituzione, test-first). In ogni storia i test si scrivono per primi e devono fallire prima del codice.

**Organizzazione**: per storia utente, ognuna provabile da sola.

## Formato: `[ID] [P?] [Storia] Descrizione`

- **[P]**: si può fare in parallelo (file diversi, nessuna dipendenza aperta)
- **[Storia]**: storia utente della spec (US1…US5)
- Percorsi: `src/main/java/it/buonacaccia/` = `main/`, `src/test/java/it/buonacaccia/` = `test/`

---

## Phase 1: Setup

**Scopo**: dati di prova e configurazione comuni.

- [ ] T001 Aggiungere `birthDate` (`LocalDate`) a `main/model/User.java` e `groupLeader` (boolean) a `main/model/Census.java`; in `main/mock/MockedIdentityProperties.java` aggiungere `birthDate` alla voce utente e `groupLeader` (Boolean) a `CensusData`, togliere `@NotNull` da `year`; in `main/mock/MockIdentityService.java` copiarli e usare l'anno corrente (`Clock`, Europe/Rome) quando `year` manca (R5)
- [ ] T002 Aggiornare `src/main/resources/mocked-identity.yml`: `socio` (user-003) con `groupId: PIE-TO-01`, `unitId: PIE-TO-01-EG`, `birthDate: 1990-04-12`, senza `year`; aggiungere user-006 `esploratore` (EG YOUTH, PIE-TO-01-EG, 2012-05-10, 100006), user-007 `lupetto` (LC YOUTH, PIE-TO-01-LC, 2017-09-02, 100007), user-008 `rover` (RS YOUTH, PIE-TO-01-RS, 2009-03-01, 100008), user-009 `rover-lig` (RS YOUTH, LIG-GE-03 / LIG-GE-03-RS, 2009-11-20, 100009), user-010 `capogruppo` (CO LEADER, PIE-TO-01 / PIE-TO-01-RS, `groupLeader: true`, 1980-01-15, 100010), user-011 `socio-non-in-regola` (CO LEADER, `NOT_IN_GOOD_STANDING`, 100011), user-012 `scolta` (RS YOUTH, PIE-TO-01-RS, 2008-06-30, 100012); tutti con ruolo `agesci:socio:user`, `isMemberOf: agesci:socio:true` ed entitlement `iscrizione:self`
- [ ] T003 [P] Aggiungere a `main/mock/MockEventTypesLoader.java` il tipo `SPEC_EG` "Campo di specialità E/G" (categoria `EG`, requisiti età 12–16, branca `EG`, `YOUTH`, `maxPerGroup` 6; adempimenti `LEADER_VALIDATION`, `PAYMENT`)
- [ ] T004 [P] Aggiornare i test che contano gli utenti mock (`test/v1/controller/MockAuthControllerTest.java`, `test/mock/MockIdentityServiceTest.java`) a 12 utenti e quelli che contano i tipi di prova a 4

---

## Phase 2: Foundational (blocca tutte le storie)

**Scopo**: modello, capienza, errori, chi chiama.

- [ ] T005 [P] Creare gli enum in `main/model/`: `EnrollmentStatus` (`REGISTERED`, `COMPLETED`, `EXPIRED`, `WITHDRAWN`, `REJECTED`, `CANCELLED`), `SeatPosition` (`IN_SEATS`, `WAITING`), `ObligationStatus` (`TODO`, `SATISFIED`, `REJECTED`, `NOT_REQUIRED`), `ClosingReason` (`WITHDRAWN`, `EXPIRED`, `REJECTED`, `EVENT_CANCELLED`, `SEATS_EXHAUSTED`)
- [ ] T006 [P] Creare `main/model/EnrollmentObligation.java` (sotto-documento con `@EqualsAndHashCode`: `type`, `description`, `status`, `satisfiedBy`, `satisfiedAt`, `note`) e `main/model/Enrollment.java` (`@Document("enrollments")`, campi di data-model.md, `@Version Long version`, indici: univoco `{eventId, arrivalOrder}`, univoco parziale `{eventId, participantId}` con `active: true`, `{participantId}`, `{presenterId}`, `{participantUnitId, status}`, `{participantGroupId, status}`, `{status, deadline}`)
- [ ] T007 [P] Creare `main/model/EventCapacity.java` (`@Document("event_capacity")`: `id` = id evento, `seats`, `lastArrival`, `inSeats`, `waiting`, `active`)
- [ ] T008 Aggiungere a `main/config/error/ErrorCode.java`: `SEATS_BELOW_ASSIGNED` 40905 (CATALOG), `ENROLLMENT_STATE_CONFLICT` 40910, `ALREADY_ENROLLED` 40911, `REGISTRATIONS_NOT_OPEN` 40912, `NOT_ELIGIBLE` 42210 (dominio `ENROLLMENT`); `STALE_VERSION` 40903 con messaggio generico e dominio `SYSTEM` (R11); aggiornare l'esempio di 40903 in `specs/002-catalogo-eventi/contracts/catalog.openapi.yaml`
- [ ] T009 Aggiungere a `main/service/guard/CallerContext.java` gli entitlement del token e `hasEntitlement(String)`
- [ ] T010 Scrivere `test/service/EventCapacityConcurrencyTest.java` (deve fallire): 200 `reserve` in parallelo su un evento con 50 posti → arrivi da 1 a 200 senza buchi né doppioni, 50 `IN_SEATS` e 150 `WAITING`, contatori `inSeats` 50, `waiting` 150, `active` 200; `release` di un posto e `promoteNext` riportano `inSeats` a 50; `updateSeats` sotto `inSeats` → 409 `40905` (SC-002, R1, R10)
- [ ] T011 Creare `main/repository/EventCapacityRepository.java` (`MongoTemplate`): `ensure(eventId, seats)` con upsert `$setOnInsert`; `reserveSeat` (`{_id, waiting: 0, $expr inSeats < seats}`, `$inc lastArrival, inSeats, active`); `reserveWaiting` (`$inc lastArrival, waiting, active`); `release(eventId, SeatPosition)`; `takeFreeSeatForWaiting` (`{waiting > 0, $expr inSeats < seats}`, `$inc inSeats +1, waiting -1`) e la sua compensazione; `setSeats(eventId, seats)` condizionato a `inSeats <= seats`; `reset(eventId)`; `find(ids)` per il catalogo e `idsByOccupancy(...)` per il filtro di disponibilità (R1, R2)
- [ ] T012 Creare `main/service/EventCapacityService.java`: `reserve(eventId, seats)` → `Reservation(arrival, position)`; `release`; `promoteNext(eventId, Consumer)` che prende il posto libero e passa nei posti la prima iscrizione attiva in attesa per `arrivalOrder` con un `findAndModify` ordinato (compensa se non ce n'è); `updateSeats(eventId, old, new)`; tutte le chiamate dentro `CircuitBreakerService` (T010 verde)

**Checkpoint**: base pronta, T010 verde.

---

## Phase 3: User Story 1 - Iscriversi da soli a un evento di qualsiasi regione (P1) 🎯 MVP

**Obiettivo**: un R/S o un capo si iscrive a un evento aperto di qualsiasi regione; i requisiti mancanti sono elencati tutti; posti e attesa sono assegnati in ordine di arrivo.

**Test indipendente**: con `rover` `POST /api/v1/events/{id}/enrollments` su un evento ROSS piemontese aperto → 201 `IN_SEATS`; `rover-lig` → 201; oltre i posti → `WAITING`; `esploratore` → 422 `42210` con i motivi.

### Test per US1 (prima del codice)

- [ ] T013 [P] [US1] Scrivere `test/service/EnrollmentEligibilityTest.java` (unitario, deve fallire): censimento assente, non in regola, anno vecchio; L/C ed E/G da soli; modalità `BY_LEADER` per iscrizione autonoma e `SELF` per quella del capo; età alla partenza con compleanno il giorno della partenza e il giorno dopo; data di nascita mancante con limiti di età; branca e tipo socio; più motivi insieme nello stesso 422
- [ ] T014 [P] [US1] Scrivere `test/service/ObligationPlannerTest.java` (unitario, deve fallire): tabella di R7 (validazione con e senza presentatore, autorizzazioni, pagamento con quota 0 / > 0 / non elencato ma quota > 0, in attesa senza pagamento); nessun adempimento → `COMPLETED`; promozione aggiunge `PAYMENT` e riporta a `REGISTERED`
- [ ] T015 [US1] Scrivere `test/v1/controller/SelfEnrollmentTest.java` (integrazione, eventi con date relative a oggi, deve fallire): iscrizione nei posti con numero, ordine di arrivo, scadenza a 9 giorni e adempimenti; evento di un'altra regione (BR-15); posti esauriti → `WAITING`, `waitingPosition`, nessun `PAYMENT`; già iscritto → 409 `40911`; iscrizioni non aperte (prima delle 09:00 dell'apertura, chiusura anticipata, bozza) → 409 `40912`; evento inesistente → 404; senza token → 401; `esterno` (non socio) e `socio-non-in-regola` → 422; `esploratore` da solo → 422 con età/branca; `oneActivePerType` e `maxPerGroup` → 422; `EventDTO.enrolledCount` e indicatore aggiornati nel catalogo

### Implementazione per US1

- [ ] T016 [P] [US1] Creare `main/repository/EnrollmentRepository.java` (`MongoRepository`: `existsByEventIdAndParticipantIdAndActiveTrue`, `countByParticipantGroupIdAndEventIdAndActiveTrue`, `existsByParticipantIdAndEventTypeCodeAndActiveTrue`, `findByParticipantId(Pageable)`, `findByPresenterId(Pageable)`) e aggiungere `findByCensusMemberCode` a `main/repository/UserRepository.java`
- [ ] T017 [P] [US1] Creare `main/service/EnrollmentEligibility.java`: `check(participant, presenterOrNull, event, now)` raccoglie i motivi `FieldErrorDTO` (`census`, `enrollmentMode`, `age`, `birthDate`, `branch`, `memberType`, `oneActivePerType`, `maxPerGroup`) e lancia 422 `42210` (T013 verde)
- [ ] T018 [P] [US1] Creare `main/service/ObligationPlanner.java`: `initial(event, position, presented)` e `onPromotion(enrollment, fee, now)` (T014 verde)
- [ ] T019 [P] [US1] Creare i DTO in `main/v1/dto/`: `EnrollmentRequestDTO` (`memberCode`), `EnrollmentDTO` (campi del contratto, `availableActions`), `EnrollmentObligationDTO`, `EnrollmentPageDTO`, `ReasonDTO` (`reason` obbligatorio, max 500), `NoteDTO` (`note` max 500) come classi Lombok; `main/v1/mapper/EnrollmentMapper.java` (MapStruct, `INSTANCE`)
- [ ] T020 [US1] Creare `main/service/EnrollmentService.java` con `enroll(caller, eventId, request)`: evento pubblicato e `REGISTRATION_OPEN` (40912), utente del token, già iscritto (40911), idoneità, `ensure` + `reserve` della capienza, adempimenti, numero `<codice>-%04d`, scadenza `now + 9 giorni`; se l'inserimento fallisce per chiave duplicata rilascia la capienza e risponde 40911 (R3)
- [ ] T021 [US1] Creare `main/v1/controller/EventEnrollmentController.java` con `POST /api/v1/events/{eventId}/enrollments` (`@SecurityAccess(entitlement = ISCRIZIONE_SELF)`, corpo facoltativo, 201) e le annotazioni OpenAPI del contratto
- [ ] T022 [US1] Catalogo: togliere `enrolledCount` da `main/model/Event.java` ed `EventService.create`; `EventStatusResolver.availability(event, enrolled, now)`; `EventService` legge gli iscritti dalla capienza per il dettaglio e per la pagina; `EventSearchRepository.availability` filtra con gli id della capienza (R2); `EventService.publish` chiama `ensure` (aggiornare `test/service/EventStatusResolverTest.java`)

**Checkpoint**: T013–T015 verdi; quickstart scenari 1–6 e 16.

---

## Phase 4: User Story 2 - Il capo unità iscrive un ragazzo della sua unità (P1)

**Obiettivo**: il capo iscrive un L/C o E/G della propria unità con il codice socio e diventa presentatore.

**Test indipendente**: con `socio` e `{"memberCode":"100006"}` su un evento `SPEC_EG` `BY_LEADER` → 201 con presentatore e validazione soddisfatta; con `100007` → 403.

- [ ] T023 [US2] Scrivere `test/v1/controller/LeaderEnrollmentTest.java` (deve fallire): iscrizione del ragazzo della propria unità con `presenterId`/`presenterName` e `LEADER_VALIDATION` `SATISFIED`; ragazzo di un'altra unità → 403; chi chiama non è capo (`rover` con un codice) → 403; codice inesistente → 404; evento `SELF` → 422 `enrollmentMode`; il capo vede l'iscrizione e il ragazzo pure
- [ ] T024 [US2] Gestire in `EnrollmentService.enroll` il ramo con `memberCode`: chi chiama deve avere censimento `LEADER` con la stessa `unitId` del ragazzo, altrimenti 403; il ragazzo deve essere `YOUTH`; presentatore = chi chiama (R4) (T023 verde)

**Checkpoint**: quickstart scenari 7–8.

---

## Phase 5: User Story 3 - Seguire lo stato dell'iscrizione fino al completamento (P2)

**Obiettivo**: pagina di stato, autorizzazioni dei capi, adempimenti della segreteria, completamento e scadenza.

**Test indipendente**: il capo gruppo autorizza, la segreteria soddisfa il pagamento → `COMPLETED`; con l'orologio spostato oltre la scadenza il job rende l'iscrizione `EXPIRED` e promuove il primo in attesa.

- [ ] T025 [US3] Scrivere `test/v1/controller/EnrollmentLifecycleTest.java` (deve fallire): `GET /enrollments/{id}` per partecipante, presentatore, capo con autorizzazione, segreteria della regione, amministrazione; 404 per gli altri (anche `segreteria-lig`); `to-authorize` del capo unità e del capo gruppo; `approve` del capo giusto, 403 per un altro capo e per il partecipante stesso, 409 `40910` se già soddisfatto; `reject` senza motivo → 422, con motivo → `REJECTED` e promozione; `satisfy` della segreteria (nota) e 403 per `segreteria-lig`; ultimo adempimento → `COMPLETED` con `completedAt`; `availableActions` coerenti; job delle scadenze con `MutableClock` +9 giorni → `EXPIRED`, posto al primo in attesa con `PAYMENT` e nuova scadenza (FR-017)
- [ ] T026 [US3] Creare `main/service/guard/EnrollmentAccessPolicy.java`: `canView`, `checkCanAuthorize(type)`, `canManage` (entitlement `iscrizione:gestione` + `EventAccessPolicy` su livello e regione dell'evento), `availableActions`
- [ ] T027 [US3] Aggiungere a `EnrollmentService`: `get`, `toAuthorize`, `approve`, `reject`, `satisfy`, il passaggio a `COMPLETED` e `exit(enrollment, reason)` che rilascia la capienza e, se era nei posti, chiama `promoteNext` con `ObligationPlanner.onPromotion`
- [ ] T028 [US3] Creare `main/service/EnrollmentJobs.java` (`@Scheduled(fixedDelay = 5 min, initialDelay = 5 min)`) con `expireOverdue()` e `cancelWaitingAtStart()` (R8); `@EnableScheduling` in `main/BuonaCacciaApplication.java`
- [ ] T029 [US3] Creare `main/v1/controller/EnrollmentController.java` con `GET /api/v1/enrollments/{id}`, `GET /api/v1/enrollments/to-authorize`, `POST /{id}/obligations/{type}/approve`, `/reject`, `/satisfy` (questo con `@SecurityAccess(entitlement = ISCRIZIONE_GESTIONE)`) (T025 verde)

**Checkpoint**: quickstart scenari 9–11 e 14.

---

## Phase 6: User Story 4 - Ritirarsi e liberare il posto (P2)

**Obiettivo**: ritiro con diritto al rimborso, promozione del primo in attesa, anche quando i posti aumentano.

**Test indipendente**: `rover` si ritira 10 giorni prima → `WITHDRAWN`, `refundDue` true, la `scolta` in attesa passa nei posti.

- [ ] T030 [P] [US4] Scrivere `test/service/RefundRuleTest.java` (unitario, deve fallire): 7 giorni prima → sì, 6 → no, quota 0 → no, in attesa → no; date nell'ora italiana a cavallo della mezzanotte
- [ ] T031 [US4] Scrivere `test/v1/controller/WithdrawalTest.java` (deve fallire): ritiro del partecipante e del presentatore; di un altro → 404; dopo la partenza o già ritirata → 409 `40910`; promozione del primo in attesa con `PAYMENT` e nuova scadenza; ritiro di chi è in attesa (nessuna promozione, posizioni degli altri aggiornate); aumento dei posti dell'evento → promozioni; riduzione sotto gli iscritti → 409 `40905`; lista d'attesa alla partenza → `CANCELLED` `SEATS_EXHAUSTED`
- [ ] T032 [US4] Creare la regola del rimborso (`RefundRule` in `main/service/`) e `EnrollmentService.withdraw` (T030 verde)
- [ ] T033 [US4] `EventService.update`: con evento pubblicato e posti cambiati chiama `EventCapacityService.updateSeats` prima del salvataggio (ripristina se il salvataggio fallisce) e poi promuove (R10); `POST /api/v1/enrollments/{id}/withdraw` in `EnrollmentController` (T031 verde)

**Checkpoint**: quickstart scenari 12–13.

---

## Phase 7: User Story 5 - Elenchi e annullamento dell'evento (P3)

**Obiettivo**: le mie iscrizioni, quelle presentate, gli iscritti per la segreteria, annullamento a cascata.

**Test indipendente**: la segreteria vede gli iscritti filtrati per posizione; dopo l'annullamento dell'evento le iscrizioni sono `CANCELLED` con il rimborso giusto.

- [ ] T034 [US5] Scrivere `test/v1/controller/EnrollmentListsTest.java` (deve fallire): `mine` e `presented` dalla più recente, con titolo e data dell'evento; elenco della segreteria in ordine di arrivo filtrato per `status` e `position`, 403 per `segreteria-lig` e per un socio; annullamento dell'evento → tutte `CANCELLED` `EVENT_CANCELLED`, `refundDue` vero solo nei posti con quota > 0, `enrolledCount` 0
- [ ] T035 [US5] Aggiungere a `EnrollmentService` `mine`, `presented`, `listForEvent` e `cancelForEvent(event)` (aggiornamento multiplo e `reset` della capienza); `GET /api/v1/enrollments/mine`, `/presented` in `EnrollmentController` e `GET /api/v1/events/{eventId}/enrollments` in `EventEnrollmentController`; `EventService.cancel` chiama `cancelForEvent` (T034 verde)

**Checkpoint**: quickstart scenari 15 e 17.

---

## Phase 8: Polish

- [ ] T036 [P] Controllare in `test/v1/controller/OpenApiDocsTest.java` che `/api-docs` esponga le nuove API sotto il tag `enrollments`
- [ ] T037 Aggiornare AGENTS.md (decisioni e limiti) e il diario; spuntare nella spec di progetto le voci della checklist chiuse dalla fase 3
- [ ] T038 `./gradlew build --max-workers=1` verde; scenari del quickstart a mano dove possibile

---

## Dipendenze

- Setup (T001–T004) → Foundational (T005–T012) → US1 → US2, US3, US4, US5.
- US2 riusa `enroll` di US1. US3 serve a US4 per `exit` e promozione. US5 dipende solo da US1.
- Nelle storie: test → modello/servizi → controller.

## Parallelismo

- T003, T004 in parallelo dopo T001–T002.
- T005, T006, T007 in parallelo; T013, T014 in parallelo; T016–T019 in parallelo.
- T030 in parallelo con T031.

## Strategia

MVP = US1 (iscrizione autonoma con lista d'attesa). Poi US2 e US3 (stato e adempimenti), US4 (ritiro e promozione), US5 (elenchi). Ogni storia chiude con la build verde.
