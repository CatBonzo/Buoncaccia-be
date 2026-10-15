# Feature Specification: Scheda elettronica

**Feature Branch**: `007-schede`

**Created**: 2026-10-15

**Status**: Draft

**Input**: User description: "Fase 7 – Scheda elettronica (spec di progetto v2.0: BR-05, US-09, FR-050, FR-051, §2.5.8, matrice dei permessi §2.6). Il modello di scheda si configura per tipo evento (sezioni di domande: cammino scout, fede, servizio, informazioni sanitarie…). Il partecipante la compila, la salva in bozza quante volte vuole e la invia: l'invio soddisfa l'adempimento. La segreteria che gestisce l'evento la legge e, su richiesta, la riapre per una correzione; il partecipante è avvisato con un'e-mail. I dati sanitari li vede solo il partecipante e la segreteria dell'evento. Fuori ambito: personale di staff dell'evento, cifratura dei dati, tempi di conservazione, candidature, Keycloak."

## Clarifications

### Session 2026-10-15

- Il modello di scheda è per tipo evento o per evento? → A (scelta di default, FR-050): per tipo evento, gestito dall'amministrazione come i tipi. Un evento che richiede la scheda lo dice con l'adempimento "scheda elettronica" (già presente nei tipi di adempimento).
- Se l'amministrazione cambia il modello dopo che qualcuno ha cominciato a compilare? → A (scelta di default): la scheda conserva una copia del modello al primo salvataggio; le modifiche valgono solo per chi non ha ancora cominciato.
- Chi compila? → A: solo il partecipante (la matrice dei permessi dice che il presentatore non vede la scheda, dati sanitari compresi). Un ragazzo iscritto da un capo compila con il proprio accesso.
- Chi legge le schede inviate? → A: il partecipante e la segreteria che gestisce l'evento (regola territoriale della fase 2). Lo staff di evento non è ancora modellato: resta fuori.
- Si può riaprire una scheda inviata? → A (FR-051): sì, la segreteria con un motivo; l'adempimento torna da fare e il partecipante ha almeno 3 giorni, come dopo il rifiuto di un documento.
- Le informazioni sanitarie hanno una protezione in più? → A (scelta di default): una domanda può essere segnata "sensibile"; le sue risposte le vede solo chi vede la scheda (già ristretto) e non compaiono in nessuna e-mail né nei registri. Cifratura e conservazione restano nella checklist della spec di progetto.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - L'amministrazione definisce il modello di scheda (Priority: P1)

L'amministrazione descrive, per un tipo evento, la scheda: sezioni con domande di tipo testo breve, testo lungo, sì/no, scelta fra opzioni o data, ciascuna obbligatoria o no e, se serve, sensibile.

**Why this priority**: senza modello non c'è nulla da compilare.

**Independent Test**: salvare un modello per un tipo, rileggerlo; modelli con identificativi doppi, scelte senza opzioni o senza domande sono rifiutati con i campi da correggere; chi non è l'amministrazione riceve 403.

**Acceptance Scenarios**:

1. **Given** l'amministrazione, **When** salva un modello valido per un tipo, **Then** viene restituito identico e sostituisce il precedente.
2. **Given** un modello con due domande con lo stesso identificativo, **When** lo si salva, **Then** 422 indica il campo.
3. **Given** un evento con l'adempimento scheda e un tipo senza modello, **When** la segreteria lo crea, **Then** 422 sul campo `obligations`.

---

### User Story 2 - Il partecipante compila, salva in bozza e invia (Priority: P1)

Il partecipante apre la scheda della sua iscrizione, vede il modello con le sue risposte, salva quante volte vuole, poi invia: le domande obbligatorie devono avere risposta e l'invio soddisfa l'adempimento (l'iscrizione si completa se non resta altro).

**Why this priority**: è la ragione della fase.

**Independent Test**: lettura di una scheda vuota; salvataggio parziale; risposte di tipo sbagliato rifiutate; invio con obbligatorie mancanti 422 con l'elenco; invio completo soddisfa e completa; dopo l'invio non si modifica più (409).

**Acceptance Scenarios**:

1. **Given** una scheda mai aperta, **When** il partecipante la legge, **Then** vede il modello e nessuna risposta, stato bozza.
2. **Given** una bozza, **When** invia con tutte le obbligatorie, **Then** la scheda è inviata, l'adempimento soddisfatto e, se non resta altro, l'iscrizione completata con la sua e-mail.
3. **Given** una scheda inviata, **When** il partecipante prova a modificarla, **Then** 409.

---

### User Story 3 - La segreteria legge e riapre (Priority: P1)

La segreteria che gestisce l'evento legge le schede inviate e può riaprirne una con un motivo: l'adempimento torna da fare, il partecipante riceve un'e-mail e corregge.

**Independent Test**: lettura da parte della segreteria giusta (200) e di un'altra regione (404); riapertura con motivo; senza motivo 422; riapertura di una bozza 409.

---

### User Story 4 - I dati li vedono solo gli aventi diritto (Priority: P1)

Presentatore, capo unità, capo gruppo, altri soci e segreterie di altre regioni non vedono la scheda: per loro non esiste (404). Le risposte non compaiono in e-mail né nei log.

**Independent Test**: lettura con ciascun utente non avente diritto → 404; le e-mail di riapertura non contengono risposte.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: L'amministrazione MUST poter salvare e leggere il modello di scheda di un tipo evento: sezioni di domande con identificativo (univoco, lettere minuscole, cifre, `_` e `-`), testo, tipo (`TEXT`, `LONG_TEXT`, `YES_NO`, `CHOICE`, `DATE`), opzioni per `CHOICE`, obbligatoria sì/no, sensibile sì/no; almeno una domanda, al massimo 60.
- **FR-002**: Un evento con l'adempimento scheda elettronica MUST essere creato solo se il suo tipo ha un modello; altrimenti 422.
- **FR-003**: Il partecipante MUST poter leggere la scheda della sua iscrizione (modello e risposte) e salvarla in bozza più volte; le risposte devono rispettare il tipo (sì/no, opzione ammessa, data valida, testo ≤ 2000 caratteri) e gli identificativi devono esistere nel modello.
- **FR-004**: La scheda MUST conservare una copia del modello al primo salvataggio.
- **FR-005**: L'invio MUST richiedere la risposta a tutte le domande obbligatorie (errore 422 con l'elenco) e, se riuscito, MUST soddisfare l'adempimento e completare l'iscrizione se non resta altro.
- **FR-006**: Dopo l'invio la scheda MUST essere immodificabile dal partecipante (409) finché la segreteria non la riapre.
- **FR-007**: La segreteria che gestisce l'evento MUST poter leggere le schede inviate e riaprirne una con un motivo obbligatorio; la riapertura riporta l'adempimento da fare con il motivo, concede almeno 3 giorni e avvisa il partecipante con un'e-mail senza risposte.
- **FR-008**: Leggere o salvare la scheda MUST essere riservato al partecipante (salvare) e a partecipante e segreteria dell'evento (leggere); per tutti gli altri la scheda non esiste (404).
- **FR-009**: Salvare, inviare e riaprire MUST essere atomici rispetto all'iscrizione: due richieste insieme non lasciano stati incoerenti (409 `40970` per il secondo).
- **FR-010**: Le risposte alle domande sensibili MUST NOT comparire in e-mail, eventi di dominio né log.
- **FR-011**: Errori MUST usare l'envelope: input non valido `42200`; azione non ammessa sullo stato della scheda `40970` (dominio `FORM`); iscrizione chiusa `40910`; permesso mancante `40300`; non visibile `40400`.

### Key Entities

- **Modello di scheda** (per tipo evento): sezioni e domande, come sopra.
- **Scheda** (una per iscrizione): copia del modello, risposte, stato (`DRAFT`, `SUBMITTED`), data di invio, chi e perché l'ha riaperta.

## Success Criteria *(mandatory)*

- **SC-001**: Un partecipante porta a termine bozza e invio con una sequenza di chiamate documentata, e l'adempimento risulta soddisfatto.
- **SC-002**: Nessun utente senza diritto ottiene mai il contenuto di una scheda (404 in ogni caso provato).
- **SC-003**: Due invii o un invio e una riapertura insieme lasciano un solo esito coerente.
- **SC-004**: Nessuna risposta compare in e-mail o log (provato sulle e-mail di riapertura e completamento).

## Assumptions

- La cifratura a riposo, i tempi di conservazione e l'accesso dello staff di evento (BR-13) restano nella checklist GDPR della spec di progetto.
- L'e-mail di completamento della fase 4 è riusata quando l'invio completa l'iscrizione.
