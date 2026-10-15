# Tasks: Scheda elettronica

Test prima del codice; si spunta solo a test verdi.

## Phase 1: Fondamenta

- [X] T001 Modello e regole: `FormTemplate`, `FormSection`, `FormQuestion`, `QuestionType`, `FormSubmission`, `FormStatus`, repository, `FormRules` (validazione del modello e delle risposte) con `FormRulesTest`; codice errore `40970`

## Phase 2: US1 Modello di scheda (P1)

- [X] T002 [US1] `test/v1/controller/FormTemplateTest.java`: salva/rilegge, sostituisce, doppioni, scelta senza opzioni, senza domande, 403 non admin, evento con scheda e tipo senza modello 422
- [X] T003 [US1] `FormTemplateService`, `FormTemplateController`, controllo in `EventService`

## Phase 3: US2 Compilazione e invio (P1) e US4 Riservatezza (P1)

- [ ] T004 [US2] `test/v1/controller/FormSubmissionTest.java`: lettura vuota, bozza parziale, tipi sbagliati, id sconosciuto, invio con obbligatorie mancanti, invio completo (adempimento e completamento), modifica dopo l'invio 409, iscrizione chiusa 409, copia del modello
- [ ] T005 [US2] `FormService.get/save/submit`, `FormController`
- [ ] T006 [US4] `test/v1/controller/FormAccessTest.java`: 404 per presentatore, capo, altro socio, segreteria di un'altra regione; segreteria giusta legge; salva solo il partecipante

## Phase 4: US3 Riapertura (P1)

- [ ] T007 [US3] `test/v1/controller/FormReopenTest.java`: riapre con motivo, adempimento `TODO` con nota, almeno 3 giorni, e-mail senza risposte, motivo obbligatorio, bozza 409, 403/404, invio e riapertura insieme, ricompila e reinvia
- [ ] T008 [US3] `FormService.reopen`, `FORM_REOPENED` (evento, modello, `MailComposer`, `RecipientResolver` con test)

## Phase 5: Rifinitura

- [ ] T009 `AGENTS.md`, diario, spec di progetto (FR-050/051), `./gradlew build --max-workers=1` verde, PR verso `feature`
