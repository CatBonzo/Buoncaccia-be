---
description: "Task della feature 004 notifiche e-mail"
---

# Tasks: Notifiche e-mail

**Input**: documenti di progetto in `specs/004-notifiche/`

**Prerequisiti**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/notifications.openapi.yaml](contracts/notifications.openapi.yaml), [quickstart.md](quickstart.md)

**Test**: obbligatori (principio II della costituzione, test-first). In ogni storia i test si scrivono per primi e devono fallire prima del codice.

**Organizzazione**: per storia utente, ognuna provabile da sola.

## Formato: `[ID] [P?] [Storia] Descrizione`

- **[P]**: si può fare in parallelo (file diversi, nessuna dipendenza aperta)
- **[Storia]**: storia utente della spec (US1…US6)
- Percorsi: `src/main/java/it/buonacaccia/` = `main/`, `src/test/java/it/buonacaccia/` = `test/`

---

## Phase 1: Setup

**Scopo**: dipendenze, configurazione e infrastruttura di test.

- [x] T001 In `build.gradle` aggiungere `spring-boot-starter-kafka` e `spring-boot-starter-mail`; nei test `org.testcontainers:testcontainers-kafka` e `org.awaitility:awaitility` (versioni dal BOM di Boot)
- [x] T002 In `src/main/resources/application.yml` aggiungere `spring.kafka` (serializzatori JSON, `group-id: buonacaccia-notifications`, `auto-offset-reset: earliest`, `acks: all`), `spring.mail` senza host di default, e `buonacaccia.notifications` (`from: noreply@buonacaccia.local`, `link-base-url: http://localhost:4200/autorizza`, `relay-delay: PT1S`, `retry.initial-interval: 1s`, `retry.multiplier: 2`, `retry.max-attempts: 4`); verificare che `test-src/env/mocked.env` e `test.env` abbiano `SPRING_KAFKA_BOOTSTRAP_SERVERS` e `SPRING_MAIL_HOST/PORT`
- [x] T003 [P] In `test/AbstractTest.java` avviare una volta per la suite `KafkaContainer` (`apache/kafka:4.3.1`) e un `GenericContainer` Mailpit (`axllent/mailpit:v1.31.5`, porte 1025 e 8025) con `@ServiceConnection` per Kafka e `@DynamicPropertySource` per `spring.mail.host/port`; proprietà di test `buonacaccia.notifications.relay-delay=PT0.2S`, `retry.initial-interval=100ms`
- [x] T004 [P] Creare `test/MailpitClient.java`: `deleteAll()`, `messagesTo(email)`, `awaitMessagesTo(email, n)` (Awaitility, 10 s), `text(id)` sull'API REST di Mailpit (`/api/v1/messages`, `/api/v1/message/{id}`, `DELETE /api/v1/messages`); pulire la casella in `@BeforeEach` di `AbstractTest`

---

## Phase 2: Foundational (blocca tutte le storie)

**Scopo**: outbox, relay, consumer, registro: il percorso completo di un evento di dominio (R1–R5).

- [x] T005 Creare `main/config/mongo/MongoTransactionConfig.java` con `MongoTransactionManager` e `TransactionTemplate` (R2)
- [x] T006 [P] Creare `main/model/DomainEventType.java` (10 valori di data-model.md), `main/model/NotificationStatus.java` (`PENDING`, `SENT`, `FAILED`, `NO_ADDRESS`, `NO_RECIPIENT`), `main/model/NotificationTemplate.java` (11 valori), `main/model/RecipientRole.java`
- [x] T007 [P] Creare `main/notification/DomainEvent.java` (record con `EnrollmentSnapshot` ed `EventSnapshot` come in data-model.md)
- [x] T008 Creare `main/model/OutboxEvent.java` (`@Document("outbox")`, indice `{status:1, createdAt:1}`, TTL 7 giorni su `publishedAt`) e `main/repository/OutboxRepository.java`
- [x] T009 Creare `main/model/Notification.java` (`@Document("notifications")`, indice unico `{domainEventId, recipientId, template}`, indici `{enrollmentId, createdAt}` e `status`) e `main/repository/NotificationRepository.java`
- [x] T010 Creare `main/notification/DomainEventRecorder.java`: costruisce gli snapshot da `Enrollment` + `Event` (con `waitingRank` contato tra le iscrizioni in attesa con `arrivalOrder` minore, `pendingObligations` = adempimenti `TODO`) e inserisce `OutboxEvent` `PENDING`; da chiamare solo dentro una transazione
- [x] T011 Creare `main/config/KafkaConfig.java`: topic `buonacaccia.domain-events` (3 partizioni) e `buonacaccia.domain-events.DLT`, `DefaultErrorHandler` con `ExponentialBackOff` dalle proprietà e `DeadLetterPublishingRecoverer`, preceduto da `main/notification/DeadLetterRecoverer.java` che segna `FAILED` le notifiche `PENDING` dell'evento (R5)
- [x] T012 Creare `main/notification/OutboxRelay.java`: `@Scheduled(fixedDelayString = relay-delay)`, al più 100 `PENDING` per `createdAt`, `send(topic, aggregateId, payload).get(5 s)`, poi `PUBLISHED`; al primo errore incrementa `attempts`, salva `lastError` e si ferma fino al giro dopo; `pause()`/`resume()` per i test (R1, R11)
- [x] T013 Creare `main/notification/MailComposer.java` (oggetto e testo per modello, date `dd/MM/yyyy` e ore `HH:mm` in Europe/Rome, R7) e `main/notification/RecipientResolver.java` (R6), con `main/repository/UserRepository.java` + `findByCensusUnitIdAndCensusMemberType` e `findByCensusGroupIdAndCensusGroupLeaderTrue`
- [x] T014 Creare `main/notification/NotificationDispatcher.java` (destinatari → notifica `PENDING` con chiave unica → invio con `JavaMailSender` → `SENT`; duplicato già chiuso = salta; `NO_ADDRESS`, `NO_RECIPIENT`) e `main/notification/DomainEventListener.java` (`@KafkaListener` sul topic, delega al dispatcher)
- [x] T015 In `main/service/EnrollmentService.java` far passare tutti i salvataggi da `save(enrollment, List<DomainEvent> events)` dentro `TransactionTemplate`; `insert` dell'iscrizione nuova idem. Gli eventi si aggiungono storia per storia
- [x] T016 [P] In `main/config/error/ErrorCode.java` aggiungere `LINK_NOT_VALID(40420, 404, "NOTIFICATION", "Il link non è valido o non lo è più.")` e `DECISION_NOT_POSSIBLE(40920, 409, "NOTIFICATION", "L'autorizzazione non è più da dare.")`
- [x] T017 [P] Test unitario `test/notification/MailComposerTest.java` (un caso per modello: oggetto con numero o codice, date italiane) e `test/notification/RecipientResolverTest.java` (partecipante, presentatore diverso, capi unità esclusa la persona stessa, capo gruppo)

**Checkpoint**: un evento scritto in outbox arriva al consumer e diventa una notifica.

---

## Phase 3: User Story 1 - Sapere subito com'è andata l'iscrizione (P1) 🎯 MVP

**Obiettivo**: e-mail di conferma al partecipante e avviso al presentatore.

**Test indipendente**: iscrivere `rover` e `esploratore` (tramite capo) e leggere le e-mail in Mailpit.

### Test (prima del codice)

- [x] T018 [US1] `test/v1/controller/EnrollmentConfirmationMailTest.java`: nei posti (una e-mail, "nei posti", adempimenti, scadenza); in attesa ("in attesa", posizione in lista, niente pagamento); tramite capo (due e-mail, modelli `ENROLLMENT_CONFIRMATION` e `PRESENTER_NOTICE`); partecipante senza e-mail (nessuna e-mail, notifica `NO_ADDRESS`)

### Implementazione

- [x] T019 [US1] In `EnrollmentService.enroll` registrare `ENROLLMENT_REGISTERED` nella transazione dell'inserimento
- [x] T020 [US1] In `NotificationDispatcher`/`MailComposer` gestire `ENROLLMENT_REGISTERED` → `ENROLLMENT_CONFIRMATION` al partecipante, `PRESENTER_NOTICE` al presentatore
- [x] T021 [US1] ~~Aggiungere un utente senza e-mail ai mock~~ Non serve: il test toglie l'e-mail a `scolta` direttamente nel database e la rimette alla fine, così gli utenti di prova restano 12

**Checkpoint**: US1 verde da sola.

---

## Phase 4: User Story 2 - Autorizzare dal link nell'e-mail (P1)

**Obiettivo**: link monouso ai capi; lettura, autorizzazione, diniego senza login (R8).

### Test (prima del codice)

- [x] T022 [P] [US2] Test unitario `test/notification/AuthorizationLinkValidityTest.java`: valido; usato; iscrizione non attiva; adempimento già soddisfatto; scadenza passata
- [x] T023 [US2] `test/v1/controller/AuthorizationLinkTest.java`: e-mail con link a ogni capo dell'unità; GET non cambia nulla; approve soddisfa l'adempimento e il secondo uso dà 404 `40420`; reject senza motivo 422, con motivo iscrizione `REJECTED`; token alterato 404; autorizzazione data dal sito → link 404; due capi: dopo il primo il link del secondo è 404; il capo che è anche partecipante non riceve il link

### Implementazione

- [x] T024 [US2] Creare `main/model/AuthorizationLink.java` (`@Document("authorization_links")`, `tokenHash` unico, `{domainEventId, leaderId}` unico) e `main/repository/AuthorizationLinkRepository.java` con `markUsed(id, decision, now)` condizionato su `usedAt: null`
- [x] T025 [US2] Creare `main/notification/AuthorizationLinkService.java`: `create(event, leader)` (token `SecureRandom` 32 byte Base64 URL, SHA-256 salvato), `read(token)`, `approve(token)`, `reject(token, reason)`; 404 `40420` per ogni link non valido, 409 `40920` se la decisione non è più possibile
- [x] T026 [US2] In `EnrollmentService` aggiungere `approveAsLeader(enrollmentId, type, leaderId)` e `rejectAsLeader(...)` che riusano la logica di `approve`/`reject` (stessi controlli `canAuthorize`, stessi eventi); in `enroll` e `promoteWaiting` registrare un `AUTHORIZATION_REQUESTED` per ogni autorizzazione `TODO`
- [x] T027 [US2] Nel dispatcher gestire `AUTHORIZATION_REQUESTED` → `AUTHORIZATION_REQUEST` a ogni capo (link creato prima dell'invio, idempotente su `{domainEventId, leaderId}`), `NO_RECIPIENT` se nessuno
- [x] T028 [US2] Creare `main/v1/dto/AuthorizationLinkDTO.java`, `LinkDecisionDTO.java` (`@NotBlank @Size(max=500) reason`), `LinkOutcomeDTO.java` e `main/v1/controller/AuthorizationLinkController.java`; in `main/config/SecurityConfig.java` rendere pubblico `/api/v1/authorization-links/**`

**Checkpoint**: US1 e US2 verdi.

---

## Phase 5: User Story 6 - Nessuna e-mail persa, nessuna doppia (P2)

**Obiettivo**: affidabilità del percorso e registro consultabile.

### Test (prima del codice)

- [x] T029 [US6] `test/v1/controller/NotificationReliabilityTest.java`: relay in pausa durante 20 iscrizioni → 201 a tutte, nessuna e-mail; ripresa → 20 conferme, una per iscrizione (SC-003); SMTP su porta chiusa → notifica `PENDING` poi `SENT` quando torna entro i tentativi, una sola e-mail; SMTP chiuso oltre i tentativi → `FAILED` e messaggio sul DLT; stesso `DomainEvent` pubblicato due volte → una sola e-mail (SC-002); registro con stato, modello, destinatario

### Implementazione

- [x] T030 [US6] Creare `main/v1/dto/NotificationDTO.java`, `main/v1/mapper/NotificationMapper.java` e `GET /api/v1/enrollments/{id}/notifications` in `main/v1/controller/EnrollmentController.java` (entitlement `iscrizione:gestione`, `EnrollmentAccessPolicy.canManage`, altrimenti 404)
- [x] T031 [US6] Rifinire `OutboxRelay`, `NotificationDispatcher` e `DeadLetterRecoverer` finché T029 è verde (conteggio `attempts`, `lastError`, ritentativo delle notifiche `PENDING`/`FAILED` alla riconsegna)

**Checkpoint**: US1, US2, US6 verdi.

---

## Phase 6: User Story 3 - Non dimenticare la scadenza (P2)

### Test (prima del codice)

- [ ] T032 [US3] `test/v1/controller/ReminderMailTest.java` con `MutableClock`: D3 a 3 giorni, D1 a 1 giorno, mai due volte; nessun promemoria per iscrizione completa, ritirata, scaduta; dopo la promozione i promemoria seguono la nuova scadenza; iscrizione a meno di un giorno riceve solo il D1

### Implementazione

- [ ] T033 [US3] Aggiungere `remindersSent` (`Set<String>`) a `main/model/Enrollment.java`; in `EnrollmentService` `sendReminders()` (R9: update condizionato su `remindersSent` + `DEADLINE_REMINDER` nella stessa transazione); chiamarlo da `main/service/EnrollmentJobs.java`
- [ ] T034 [US3] Nel dispatcher e nel composer il modello `DEADLINE_REMINDER` con adempimenti mancanti e scadenza

---

## Phase 7: User Story 4 - Essere avvisati quando l'iscrizione cambia (P2)

### Test (prima del codice)

- [ ] T035 [US4] `test/v1/controller/EnrollmentChangeMailTest.java`: promozione (nei posti, pagamento se quota > 0, nuova scadenza); ritiro con e senza rimborso; rifiuto con motivo; completamento; scadenza; annullamento per posti esauriti alla partenza; il presentatore riceve le stesse e-mail

### Implementazione

- [ ] T036 [US4] In `EnrollmentService` registrare `ENROLLMENT_COMPLETED` (in `completeIfDone` quando cambia lo stato), `ENROLLMENT_EXPIRED`, `ENROLLMENT_WITHDRAWN`, `ENROLLMENT_PROMOTED`, `ENROLLMENT_REJECTED`, `ENROLLMENT_CANCELLED` nei rispettivi salvataggi (`exit`, `tryExit`, `cancelAll`, `promoteWaiting`)
- [ ] T037 [US4] Nel dispatcher e nel composer i sei modelli corrispondenti, al partecipante e al presentatore

---

## Phase 8: User Story 5 - Essere avvisati quando l'evento cambia (P3)

### Test (prima del codice)

- [ ] T038 [US5] `test/v1/controller/EventChangeMailTest.java`: tre iscritti, cambio di luogo → tre e-mail con il nuovo luogo; cambio di date idem; cambio solo di descrizione o posti → nessuna e-mail; annullamento → un'e-mail di annullamento per iscritto attivo con motivo e rimborso

### Implementazione

- [ ] T039 [US5] In `main/service/EventService.java` confrontare `startDate`, `endDate`, `location` in `update` e, per un evento non bozza con cambi, registrare `EVENT_CHANGED` nella stessa transazione del salvataggio
- [ ] T040 [US5] Nel dispatcher `EVENT_CHANGED` → `EVENT_CHANGED` a ogni partecipante delle iscrizioni attive (R6); per l'annullamento verificare che `cancelForEvent` produca già `ENROLLMENT_CANCELLED` con `EVENT_CANCELLED` (T036)

---

## Phase 9: Polish

- [ ] T041 [P] Aggiornare `specs/004-notifiche/quickstart.md` con quanto verificato a mano e `AGENTS.md` (decisioni R1–R12 con il perché)
- [ ] T042 [P] Aggiornare il diario `docs/tesi/diario-capitolo-3.txt`
- [ ] T043 `./gradlew build --max-workers=1` verde; rivedere i log a `debug` di una iscrizione (relay, consumer, invio) che siano leggibili

---

## Dipendenze

- Setup (T001–T004) → Foundational (T005–T017) → storie.
- US1 → US2 (usa `ENROLLMENT_REGISTERED` e il dispatcher) → US6 (prova su conferme già funzionanti).
- US3, US4, US5 dipendono solo dalla Foundational e da US1; si possono fare in qualsiasi ordine dopo US6.

## Parallelismo

- T003/T004, T006/T007/T016/T017 su file diversi.
- In US2 T022 (unitario) insieme a T023.

## Strategia

MVP = Setup + Foundational + US1: una conferma di iscrizione che arriva in Mailpit passando da outbox e Kafka. Poi US2 (il valore più alto per le segreterie), US6 (la garanzia), quindi US3–US5.

## Note di implementazione

- T021: nessun utente nuovo nei mock; il test del caso `NO_ADDRESS` toglie l'e-mail a `scolta` nel database e la rimette alla fine.
- T026: `AUTHORIZATION_REQUESTED` si scrive solo all'iscrizione. Le autorizzazioni nascono già alla registrazione anche per chi è in attesa (`ObligationPlanner.initial`); alla promozione si aggiunge solo il pagamento, quindi non c'è un'altra richiesta da mandare.
- T028: il diniego dal link riusa `ReasonDTO` della fase 3 (stesso vincolo `@NotBlank @Size(max=500)`) invece di un `LinkDecisionDTO` nuovo.
- T003: `MongoDBContainer` di Testcontainers 2 parte standalone; con le transazioni della 004 serve `.withReplicaSet()`, come il replica set di docker-compose.
