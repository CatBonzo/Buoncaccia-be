# Implementation Plan: Notifiche e-mail

**Branch**: `004-notifiche` | **Date**: 2026-10-14 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/004-notifiche/spec.md`

## Summary

Ogni cambio di stato di un'iscrizione (e la modifica di date o luogo di un evento) scrive, nella stessa transazione MongoDB del cambio, un evento di dominio nella collezione `outbox`. Un relay pubblica gli eventi in attesa sul topic Kafka `buonacaccia.domain-events` e li segna pubblicati; se Kafka è fermo restano lì e partono alla ripresa. Un consumer Kafka sceglie i destinatari, compone l'e-mail di testo in italiano e la invia via SMTP (Mailpit in locale). Ogni invio è una `Notification` con chiave unica (evento di dominio, destinatario, modello): un evento consegnato due volte non manda due e-mail. Gli errori di invio sono ritentati dal gestore di errori di Spring Kafka con attese crescenti; esauriti i tentativi il messaggio va nel topic `.DLT` e le notifiche restano `FAILED`. Per le autorizzazioni dei capi il consumer crea un link monouso (token casuale, salvato solo come hash) che il capo usa senza login. I promemoria a 3 e 1 giorno li produce il job delle iscrizioni. Dettagli in [research.md](research.md).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: come 001–003, più `spring-boot-starter-kafka` e `spring-boot-starter-mail`; nei test `testcontainers-kafka` e un container Mailpit (`GenericContainer`)

**Storage**: MongoDB 8.0 (replica set); nuove collezioni `outbox`, `notifications`, `authorization_links`; campo nuovo `Enrollment.remindersSent`; `MongoTransactionManager` per legare cambio di stato ed evento di dominio

**Testing**: JUnit 5, Spring Boot Test, Testcontainers (MongoDB, Kafka, Mailpit), `MutableClock`; Awaitility (già nel BOM di Spring Boot) per aspettare l'arrivo delle e-mail

**Target Platform**: applicazione Spring Boot su PC di sviluppo (Docker Compose con Kafka e Mailpit già presenti), CI su GitHub Actions

**Project Type**: web service (REST API per un frontend separato)

**Performance Goals**: SC-001, e-mail in casella entro 10 secondi in locale (relay ogni secondo)

**Constraints**: nessun evento perso (NFR-06), nessuna e-mail doppia; contratto `/api/v1` senza rotture rispetto a 001–003; una sola istanza dell'applicazione; ora italiana nelle e-mail

**Scale/Scope**: 3 collezioni nuove, 1 topic più il suo DLT, 4 endpoint nuovi (3 pubblici sul link, 1 registro), 12 modelli di e-mail

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Verifica | Esito |
| --- | --- | --- |
| I. Qualità del codice | Package nuovo `notification` per ciò che riguarda solo le notifiche (outbox, relay, consumer, composizione, link); i servizi esistenti chiamano solo `DomainEventRecorder`; controller senza logica | ✅ |
| II. Test-first | Test di integrazione per US1–US6 con Kafka e Mailpit veri (Testcontainers) scritti prima; test unitari per destinatari, testi e validità del link | ✅ |
| III. Coerenza con i client | Contratto OpenAPI in `contracts/` prima del codice; stesso envelope; nuovo dominio `NOTIFICATION` (40420, 40920); le API di 003 non cambiano | ✅ |
| IV. Semplicità | Outbox su MongoDB invece di CDC o transazioni Kafka; e-mail di testo con `String.formatted` invece di un motore di template; token casuale con hash invece di JWT firmati; un solo topic | ✅ |
| Vincoli tecnologici | Java 21, Boot 4.1, Gradle Groovy, MongoDB, Kafka (ADR-003, confermato da Mahima il 14/10) | ✅ |

Ricontrollo dopo la Fase 1 (design): ✅ nessuna violazione. Un cambio a codice esistente: i salvataggi di `EnrollmentService` passano da un metodo che, nella stessa transazione, scrive anche l'evento di dominio (R2). Le operazioni sulla capienza restano fuori dalla transazione come nella fase 3.

## Project Structure

### Documentation (this feature)

```text
specs/004-notifiche/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── notifications.openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

Solo i file nuovi o toccati.

```text
build.gradle                                   # + kafka, mail; test: testcontainers-kafka
src/main/resources/application.yml             # + spring.kafka, spring.mail, buonacaccia.notifications.*
src/main/java/it/buonacaccia/
├── config/
│   ├── KafkaConfig.java                       # topic, DLT, gestore errori con backoff
│   ├── mongo/MongoTransactionConfig.java      # MongoTransactionManager + TransactionTemplate
│   ├── SecurityConfig.java                    # /api/v1/authorization-links/** pubblico
│   └── error/ErrorCode.java                   # + dominio NOTIFICATION (40420, 40920)
├── model/
│   ├── Enrollment.java                        # + remindersSent
│   ├── OutboxEvent.java, DomainEventType.java
│   ├── Notification.java, NotificationStatus.java, NotificationTemplate.java
│   └── AuthorizationLink.java
├── repository/
│   ├── OutboxRepository.java, NotificationRepository.java, AuthorizationLinkRepository.java
├── notification/
│   ├── DomainEvent.java                       # record del messaggio Kafka (snapshot)
│   ├── DomainEventRecorder.java               # crea lo snapshot e scrive in outbox
│   ├── OutboxRelay.java                       # @Scheduled: outbox → Kafka
│   ├── DomainEventListener.java               # @KafkaListener
│   ├── NotificationDispatcher.java            # destinatari, idempotenza, invio, registro
│   ├── RecipientResolver.java                 # partecipante, presentatore, capi, iscritti attivi
│   ├── MailComposer.java                      # oggetto e testo per modello
│   ├── AuthorizationLinkService.java          # crea, legge, usa il link
│   └── DeadLetterRecoverer.java               # segna FAILED dopo l'ultimo tentativo
├── service/
│   ├── EnrollmentService.java                 # salvataggi con evento di dominio; approve/reject dal link
│   ├── EnrollmentJobs.java                    # + promemoria
│   └── EventService.java                      # + evento di dominio su date o luogo cambiati
└── v1/
    ├── controller/AuthorizationLinkController.java, EnrollmentController.java (+ registro)
    ├── dto/AuthorizationLinkDTO, LinkDecisionDTO, NotificationDTO
    └── mapper/NotificationMapper.java

src/test/java/it/buonacaccia/
├── AbstractTest.java                          # + container Kafka e Mailpit, client Mailpit
├── MailpitClient.java
├── notification/RecipientResolverTest.java, MailComposerTest.java, AuthorizationLinkValidityTest.java
└── v1/controller/EnrollmentConfirmationMailTest.java (US1), AuthorizationLinkTest.java (US2),
    ReminderMailTest.java (US3), EnrollmentChangeMailTest.java (US4), EventChangeMailTest.java (US5),
    NotificationReliabilityTest.java (US6)
```

**Structure Decision**: le notifiche sono un sottosistema a sé, raggiunto dal resto solo tramite `DomainEventRecorder` (scrittura) e il topic Kafka (lettura). Per questo hanno un package proprio `notification` invece di spargersi tra `service` e `repository`: se un giorno diventassero un servizio separato, si sposta il package e il topic resta il contratto. Modelli, repository e controller restano nei package di sempre, come nello stile ScoutShopper.

## Complexity Tracking

| Violazione | Perché serve | Alternativa più semplice scartata perché |
| --- | --- | --- |
| Package `notification` fuori dallo schema config/guard/model/repository/service/v1 | Raccoglie il flusso asincrono (outbox, relay, consumer) che non è un servizio REST | Metterlo in `service` mescolerebbe classi chiamate dai controller con classi chiamate da Kafka e dallo scheduler |
