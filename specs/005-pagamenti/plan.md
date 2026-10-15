# Implementation Plan: Pagamenti e rimborsi registrati dalla segreteria

**Branch**: `005-pagamenti` | **Date**: 2026-10-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/005-pagamenti/spec.md`

## Summary

La segreteria che gestisce l'evento registra i pagamenti ricevuti (bonifico, contanti) su un'iscrizione nei posti. Ogni pagamento è un documento della collezione `payments`; importi sempre in centesimi interi (`long`), così le somme sono esatte e il controllo "non oltre la quota" è un confronto fra interi. Registrare un pagamento aggiorna nella stessa transazione MongoDB: il pagamento, il versato dell'iscrizione (con `@Version`, quindi due registrazioni insieme non si sommano male), l'adempimento di pagamento e l'eventuale completamento, più l'evento di dominio per l'e-mail (outbox della fase 4). Quando un'iscrizione con versato maggiore di 0 si chiude con diritto al rimborso (ritiro BR-10, scadenza, diniego, evento annullato), la stessa transazione di chiusura crea una richiesta di rimborso (`refunds`, una per iscrizione). La segreteria la lavora con tre azioni (prende in carico, emette, rifiuta); ogni passaggio produce un'e-mail. La riconciliazione di un evento si calcola dai pagamenti e dai rimborsi e si scarica in CSV. Le coordinate di pagamento (IBAN, contatti) stanno in `region_settings`, una per regione più quella nazionale, e compaiono nell'iscrizione. Dettagli in [research.md](research.md).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: come 001–004, nessuna dipendenza nuova

**Storage**: MongoDB 8.0 (replica set); nuove collezioni `payments`, `refunds`, `region_settings`; campi nuovi su `enrollments` (`feeCents`, `paidCents`)

**Testing**: JUnit 5, Spring Boot Test, Testcontainers (MongoDB, Kafka, Mailpit), `MutableClock`; test unitari per il controllo dell'IBAN, il formato del CSV e gli importi

**Target Platform**: applicazione Spring Boot su PC di sviluppo (Docker Compose), CI su GitHub Actions

**Project Type**: web service (REST API per un frontend separato)

**Performance Goals**: SC-002, e-mail di conferma entro 10 secondi in locale (relay della fase 4)

**Constraints**: contratto `/api/v1` senza rotture rispetto a 001–004 (`EnrollmentDTO` solo con campi nuovi); una sola istanza; ora italiana; nessun gateway esterno

**Scale/Scope**: 3 collezioni nuove, 12 endpoint nuovi, 4 modelli di e-mail nuovi, 1 utente di prova non necessario (le segreterie PIE e LIG hanno già `pagamento:registrazione`)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Verifica | Esito |
| --- | --- | --- |
| I. Qualità del codice | Controller in `v1.controller` senza logica; regole di pagamento in `PaymentService`, di rimborso in `RefundService`, validazione IBAN in `Iban`, importi in `Money`; permessi riusano `EventAccessPolicy`; conversioni con MapStruct | ✅ |
| II. Test-first | Test di integrazione per US1–US5 e i casi limite scritti prima; test unitari per IBAN, importi e CSV | ✅ |
| III. Coerenza con i client | Contratto OpenAPI in `contracts/` prima del codice; stesso envelope; nuovi domini `PAYMENT` (40930, 40931) e `REFUND` (40940); le API di 003 non cambiano nella forma | ✅ |
| IV. Semplicità | Importi in centesimi invece di `Decimal128`; versione ottimistica dell'iscrizione invece di lock; il rimborso nasce nella transazione di chiusura senza code o job; CSV scritto a mano | ✅ |
| Vincoli tecnologici | Java 21, Boot 4.1, Gradle Groovy, MongoDB; entitlement `pagamento:registrazione` + regola territoriale della fase 2 | ✅ |

Ricontrollo dopo la Fase 1 (design): ✅ nessuna violazione. Cambi a codice esistente, tutti compatibili: `Enrollment` guadagna `feeCents` e `paidCents`; la chiusura di un'iscrizione (`exit`, `cancelAll`) crea il rimborso; `EnrollmentDTO` guadagna `payment`, `paymentInfo` e `refund`.

## Project Structure

### Documentation (this feature)

```text
specs/005-pagamenti/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── payments.openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

Solo i file nuovi o toccati.

```text
src/main/java/it/buonacaccia/
├── config/error/ErrorCode.java                # + PAYMENT_DUPLICATE 40930, PAYMENT_CANCELLED 40931, REFUND_TRANSITION 40940
├── model/
│   ├── Enrollment.java                        # + feeCents, paidCents
│   ├── Payment.java, PaymentMethod.java, PaymentStatus.java
│   ├── Refund.java, RefundStatus.java
│   ├── RegionSettings.java
│   └── DomainEventType.java, NotificationTemplate.java   # + PAYMENT_*, REFUND_*
├── repository/
│   ├── PaymentRepository.java, RefundRepository.java, RegionSettingsRepository.java
├── notification/
│   ├── DomainEvent.java                       # + MoneySnapshot
│   ├── DomainEventRecorder.java               # + payment(), refund()
│   ├── MailComposer.java, RecipientResolver.java   # + 4 modelli
├── service/
│   ├── Money.java                             # euro <-> centesimi
│   ├── Iban.java                              # formato e cifre di controllo
│   ├── PaymentService.java                    # registra, annulla, elenca
│   ├── RefundService.java                     # crea alla chiusura, coda, transizioni
│   ├── ReconciliationService.java             # totali e CSV
│   ├── RegionSettingsService.java
│   └── EnrollmentService.java                 # feeCents all'iscrizione, rimborso in exit/cancelAll, DTO con pagamento
└── v1/
    ├── controller/PaymentController.java, RefundController.java, EventPaymentController.java,
    │              RegionSettingsController.java
    ├── dto/ PaymentRequestDTO, PaymentDTO, PaymentSummaryDTO, PaymentInfoDTO, CancelReasonDTO ... RefundDTO,
    │        RefundIssueDTO, ReconciliationDTO, RegionSettingsDTO
    └── mapper/PaymentMapper.java, RefundMapper.java, RegionSettingsMapper.java

src/test/java/it/buonacaccia/
├── service/IbanTest.java, MoneyTest.java, ReconciliationCsvTest.java
└── v1/controller/PaymentInfoTest.java (US1), PaymentRegistrationTest.java (US2), RefundFlowTest.java (US3),
    ReconciliationTest.java (US4), RegionSettingsTest.java (US5)
```

**Structure Decision**: stessa applicazione e stessi package della fase 3. Il flusso asincrono delle e-mail resta nel package `notification` della fase 4, che qui riceve soltanto quattro tipi di evento in più.

## Complexity Tracking

Nessuna violazione della costituzione da giustificare.
