---
description: "Task della feature 005 pagamenti e rimborsi"
---

# Tasks: Pagamenti e rimborsi registrati dalla segreteria

**Input**: documenti di progetto in `specs/005-pagamenti/`

**Prerequisiti**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/payments.openapi.yaml](contracts/payments.openapi.yaml), [quickstart.md](quickstart.md)

**Test**: obbligatori (principio II della costituzione, test-first). In ogni storia i test si scrivono per primi e devono fallire prima del codice.

**Organizzazione**: per storia utente, ognuna provabile da sola.

## Formato: `[ID] [P?] [Storia] Descrizione`

- **[P]**: si può fare in parallelo (file diversi, nessuna dipendenza aperta)
- **[Storia]**: storia utente della spec (US1…US5)
- Percorsi: `src/main/java/it/buonacaccia/` = `main/`, `src/test/java/it/buonacaccia/` = `test/`

---

## Phase 1: Setup e fondamenta

**Scopo**: modelli, importi, errori e dati comuni a tutte le storie.

- [x] T001 [P] Creare `main/service/Money.java` (`toCents(BigDecimal)` rifiuta ≤ 0 e più di due decimali con `IllegalArgumentException`; `toEuro(long)` restituisce scala 2) e il test unitario `test/service/MoneyTest.java` (R1)
- [x] T002 [P] Creare `main/model/PaymentMethod.java` (`BANK_TRANSFER`, `OFFICE_CASH`), `PaymentStatus.java` (`VALID`, `CANCELLED`), `RefundStatus.java` (`REQUESTED`, `IN_PROGRESS`, `ISSUED`, `REJECTED`)
- [x] T003 Creare `main/model/Payment.java` e `main/model/Refund.java` con le annotazioni della regola e gli indici di data-model.md (`refunds.enrollmentId` unico), `main/repository/PaymentRepository.java` e `RefundRepository.java`
- [x] T004 Aggiungere a `main/model/Enrollment.java` `Long feeCents` e `long paidCents`; in `EnrollmentService.enroll` impostare `feeCents` dalla quota dell'evento (R3) e `paidCents` a 0
- [x] T005 [P] In `main/config/error/ErrorCode.java` aggiungere `PAYMENT_DUPLICATE(40930, 409, "PAYMENT", ...)`, `PAYMENT_CANCELLED(40931, 409, "PAYMENT", ...)`, `REFUND_TRANSITION(40940, 409, "REFUND", ...)`
- [x] T006 Aggiungere ai tipi di `DomainEventType` `PAYMENT_REGISTERED`, `REFUND_REQUESTED`, `REFUND_ISSUED`, `REFUND_REJECTED`, a `NotificationTemplate` i quattro modelli corrispondenti, a `DomainEvent` il record `MoneySnapshot(amount, paid, due, date, reference, reason)` (nuovo componente `money`, null per i vecchi) e a `DomainEventRecorder` i metodi `payment(...)` e `refund(...)`; aggiornare i costruttori di `DomainEvent` nei test esistenti (`MailComposerTest`, `RecipientResolverTest`)
- [x] T007 In `main/notification/RecipientResolver.java` i quattro tipi nuovi vanno a partecipante e presentatore; in `MailComposer.java` i quattro testi (R8): pagamento ricevuto con importo/versato/residuo e "iscrizione completa"; rimborso richiesto, emesso (importo, data, riferimento), rifiutato (motivo); estendere `MailComposerTest` e `RecipientResolverTest`

**Checkpoint**: il progetto compila e i test esistenti sono verdi.

---

## Phase 2: User Story 5 - Coordinate di pagamento della regione (P3, ma serve alla US1)

**Obiettivo**: IBAN e contatti per regione, validati.

### Test (prima del codice)

- [x] T008 [P] [US5] `test/service/IbanTest.java`: IBAN italiano valido (`IT60X0542811101000000123456`), con spazi e minuscole normalizzato, cifre di controllo errate, lunghezza sbagliata per il paese, caratteri non ammessi, paese sconosciuto
- [x] T009 [US5] `test/v1/controller/RegionSettingsTest.java`: la segreteria PIE salva e legge le proprie coordinate; IBAN non valido 422 col campo `iban`; segreteria LIG non può cambiare PIE (403); `NAT` solo amministrazione; sigla inesistente 404; lettura di una regione mai impostata con campi null

### Implementazione

- [x] T010 [US5] Creare `main/service/Iban.java` (formato e modulo 97, lunghezza per paese per i paesi europei più comuni, normalizzazione)
- [x] T011 [US5] Creare `main/model/RegionSettings.java`, `main/repository/RegionSettingsRepository.java`, `main/service/RegionSettingsService.java` (permessi con `EventAccessPolicy`, R10), `main/v1/dto/RegionSettingsDTO.java`, `main/v1/mapper/RegionSettingsMapper.java` e `main/v1/controller/RegionSettingsController.java` (`@SecurityAccess(entitlement = PAGAMENTO_REGISTRAZIONE)`)

---

## Phase 3: User Story 1 - Sapere quanto devo pagare e a chi (P1) 🎯 MVP

**Test indipendente**: iscrizione a un evento con quota e lettura di `payment` e `paymentInfo`.

### Test (prima del codice)

- [x] T012 [US1] `test/v1/controller/PaymentInfoTest.java`: iscrizione nei posti con quota (fee, paid 0, due = fee, IBAN e contatti della regione); in attesa (quota ma niente `paymentInfo`); evento gratuito (nessun `payment`); regione senza impostazioni (`paymentInfo` con campi null); evento nazionale (impostazioni `NAT`)

### Implementazione

- [x] T013 [US1] Creare `main/v1/dto/PaymentSummaryDTO.java` e `PaymentInfoDTO.java`; aggiungere `payment`, `paymentInfo` (e `refund`, vuoto per ora) a `EnrollmentDTO`; in `EnrollmentService.toDto` valorizzarli (R9) leggendo `RegionSettingsService.find(...)`

**Checkpoint**: US1 e US5 verdi.

---

## Phase 4: User Story 2 - La segreteria registra un pagamento (P1)

### Test (prima del codice)

- [x] T014 [US2] `test/v1/controller/PaymentRegistrationTest.java`: pagamento pieno → adempimento soddisfatto, iscrizione `COMPLETED` se non resta altro, e-mail al socio; pagamento parziale e poi il resto; oltre il residuo 422 col campo `amount`; doppione 409 `40930`; iscrizione in attesa o chiusa 409 `40910`; segreteria LIG su evento PIE 403 e socio 403; annullamento col motivo (versato ricalcolato, adempimento di nuovo da fare, `COMPLETED` → `REGISTERED` con scadenza nuova); annullamento due volte 409 `40931`; due registrazioni contemporanee: una sola riesce (R2); elenco dei pagamenti visibile a partecipante e segreteria, non agli altri (404)

### Implementazione

- [x] T015 [US2] Rendere `EnrollmentService.completeIfDone` accessibile al package `service` (static, package-private)
- [x] T016 [US2] Creare `main/service/PaymentService.java` (`register`, `cancel`, `list` come R4, R5; transazione, un solo riprovo su `TransientTransactionError`), `main/v1/dto/PaymentRequestDTO.java` (`@NotNull @DecimalMin("0.01") amount`, `@NotNull method`, `@NotNull receivedOn`, `@Size(max=100) reference`), `PaymentDTO.java`, `ReasonDTO` già esistente per l'annullamento, `main/v1/mapper/PaymentMapper.java` e `main/v1/controller/PaymentController.java`
- [x] T017 [US2] Verificare che le e-mail di `PAYMENT_REGISTERED` arrivino (T007) e che `satisfy` della fase 3 sul tipo `PAYMENT` resti invariato (R4, nota)

**Checkpoint**: US1, US2, US5 verdi.

---

## Phase 5: User Story 3 - Il rimborso nasce da solo e la segreteria lo lavora (P1)

### Test (prima del codice)

- [ ] T018 [US3] `test/v1/controller/RefundFlowTest.java`: ritiro con pagamento ≥ 7 giorni prima → rimborso `REQUESTED` dell'importo versato e e-mail; ritiro a meno di 7 giorni → nessun rimborso; scadenza con pagamento parziale e diniego di un'autorizzazione → rimborso del versato; evento annullato → un rimborso per iscrizione pagata e nessuno per chi non ha pagato né per chi era in attesa; iscrizione senza pagamenti → nessun rimborso; `take` → `IN_PROGRESS`; `issue` con data e riferimento → `ISSUED` ed e-mail; `reject` con motivo → `REJECTED` ed e-mail; transizione non ammessa 409 `40940`; coda filtrata per stato e per regione (segreteria LIG non vede i rimborsi PIE); `issue` con data futura 422; il rimborso compare nell'iscrizione (`refund`)

### Implementazione

- [ ] T019 [US3] Creare `main/service/RefundService.java`: `requestFor(Enrollment)` (R6), `list`, `take`, `issue`, `reject` con aggiornamento condizionato sullo stato di partenza (R7); `main/v1/dto/RefundDTO.java`, `RefundIssueDTO.java`, `RefundPageDTO.java`, `main/v1/mapper/RefundMapper.java`, `main/v1/controller/RefundController.java`
- [ ] T020 [US3] In `EnrollmentService.exit` e `cancelAll` chiamare `RefundService.requestFor` nella transazione di chiusura; per scadenza e diniego impostare `refundDue = paidCents > 0` (R6); valorizzare `EnrollmentDTO.refund`

**Checkpoint**: US1, US2, US3, US5 verdi.

---

## Phase 6: User Story 4 - Quadro dei pagamenti di un evento (P2)

### Test (prima del codice)

- [ ] T021 [P] [US4] `test/service/ReconciliationCsvTest.java`: intestazione, una riga per pagamento e per rimborso, campi con `;`, `"` e a capo fra virgolette, importi con il punto decimale e due cifre, date `dd/MM/yyyy`
- [ ] T022 [US4] `test/v1/controller/ReconciliationTest.java`: tre pagamenti e un rimborso → totali (incassato, annullato, da rimborsare, rimborsato) e iscrizioni con residuo; CSV con le stesse righe; segreteria di un'altra regione 403; evento inesistente 404; invariante SC-004

### Implementazione

- [ ] T023 [US4] Creare `main/service/ReconciliationService.java` (totali, iscrizioni con residuo, CSV a mano, R11), `main/v1/dto/ReconciliationDTO.java` e `main/v1/controller/EventPaymentController.java`

---

## Phase 7: Polish

- [ ] T024 [P] Aggiornare `specs/005-pagamenti/quickstart.md` con quanto verificato a mano, `AGENTS.md` (decisioni R1–R13 con il perché) e `docs/tesi/diario-capitolo-3.txt`
- [ ] T025 [P] Aggiornare la spec di progetto in `/mnt/project-files/spec/` (.md e .docx): checklist e FR-041/FR-045 adattati (registrazione dalla segreteria, niente ricevuta caricata)
- [ ] T026 `./gradlew build --max-workers=1` verde; rivedere i log a `debug` di un pagamento e di un rimborso

---

## Dipendenze

- Setup e fondamenta (T001–T007) → storie.
- US5 → US1 (le impostazioni servono alla pagina dell'iscrizione) → US2 → US3 (il rimborso presuppone pagamenti registrati) → US4.

## Parallelismo

- T001/T002/T005, T008, T021 su file diversi.

## Strategia

MVP = fondamenta + US5 + US1: la pagina dell'iscrizione che dice quanto e dove pagare. Poi US2 (registrazione, il valore maggiore per la segreteria), US3 (chiude il ciclo del denaro), US4.
