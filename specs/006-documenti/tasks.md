# Tasks: Documenti richiesti, caricamento e verifica

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md). Test prima del codice; si ticchetta solo a test verdi.

## Phase 1: Fondamenta

- [X] T001 [P] `test/service/FileTypeDetectorTest.java` e `main/service/FileTypeDetector.java` (JPEG, PNG, PDF dai primi byte; tutto il resto sconosciuto; file vuoto)
- [X] T002 Modello: `Obligation` e `EnrollmentObligation` con `code`, `delivery`, `allowedMimeTypes`, `maxSizeBytes`; `ObligationStatus.IN_REVIEW`; `ObligationDTO` e `EnrollmentObligationDTO` coerenti (default JPEG/PNG/PDF e 5 MB in `DocumentRules`, con test)
- [X] T003 `Document`, `DocumentStatus`, `DocumentRepository`; `DocumentStorage` e `GridFsDocumentStorage`; codici d'errore `40960`
- [X] T004 `ObligationPlanner`: un adempimento per documento (R4), `statusFor` con `IN_REVIEW` (R5); test aggiornati (`ObligationPlannerTest` se esiste)

## Phase 2: US1 Documenti richiesti dall'evento (P1)

- [X] T005 [US1] `test/v1/controller/EventDocumentsTest.java`: due documenti con vincoli, iscrizione con due adempimenti, default, codici duplicati e dimensione oltre 10 MB 422, evento senza codice compatibile
- [X] T006 [US1] Validazione dei documenti in `EventService` e mappatura DTO

## Phase 3: US2 Caricamento (P1) e US4 Visibilità (P1)

- [X] T007 [US2] `test/v1/controller/DocumentUploadTest.java`: caricamento valido → `IN_REVIEW`; estensione `.jpg` ma byte di un eseguibile, formato non ammesso, vuoto, oltre la dimensione → 422 campo `file`; sostituzione; dopo la validazione 409; cartaceo 409; iscrizione chiusa 409; terzi 404; due caricamenti insieme → un solo `UPLOADED`
- [X] T008 [US2] `main/service/DocumentService.upload`, `DocumentController` (multipart), supporto multipart nel test (`AbstractTest`)
- [X] T009 [US4] `test/v1/controller/DocumentAccessTest.java`: elenco e scarico per partecipante, presentatore, segreteria giusta; 404 per altro socio e segreteria di un'altra regione; sostituiti e rifiutati scaricabili
- [X] T010 [US4] `DocumentService.list` e `content`, scarico con nome e tipo originali

## Phase 4: US3 Verifica (P1)

- [ ] T011 [US3] `test/v1/controller/DocumentReviewTest.java`: valida → soddisfatto e iscrizione completa con l'e-mail della fase 4; rifiuta con motivo → `TODO`, e-mail col motivo; senza motivo 422; 403 altra regione e socio; già validato/rifiutato 409; due segreterie insieme; `satisfy?code=` per un cartaceo; scadenza e promemoria ignorati con soli `IN_REVIEW`
- [ ] T012 [US3] `DocumentService.validate/reject`, `DocumentEventType`/modello `DOCUMENT_REJECTED` con test di `MailComposer` e `RecipientResolver`, `satisfy?code=`, filtri di scadenza e promemoria in `EnrollmentService`

## Phase 5: US5 Coda (P2)

- [ ] T013 [US5] `test/v1/controller/DocumentQueueTest.java` e `DocumentService.queue` (regione, evento, stato, pagine)

## Phase 6: Rifinitura

- [ ] T014 `AGENTS.md` (decisioni R1-R9 con il perché), `docs/tesi/diario-capitolo-3.txt`, spec di progetto (FR-047..049, checklist), `./gradlew build --max-workers=1` verde, PR verso `feature`
