# Feature Specification: Documenti richiesti, caricamento e verifica

**Feature Branch**: `006-documenti`

**Created**: 2026-10-15

**Status**: Draft

**Input**: User description: "Fase 6 – Documenti (spec di progetto v2.0: BR-05, US-09, FR-047, FR-048, FR-049, §2.5.8; la scheda elettronica FR-050/051 è la fase successiva). L'evento definisce i documenti richiesti con vincoli (formato, dimensione) oppure cartacei. Il socio, o il suo presentatore, carica i file sull'iscrizione; il sistema controlla tipo reale e dimensione; l'adempimento passa in verifica. La segreteria che gestisce l'evento scarica, valida o rifiuta con un motivo; un rifiuto riporta l'adempimento da fare e avvisa il socio con un'e-mail. I documenti cartacei li segna ricevuti la segreteria. I file sono visibili solo a chi di dovere. Fuori ambito: scheda elettronica e liberatoria compilata online (fase successiva), antivirus, MinIO, candidature, Keycloak."

## Clarifications

### Session 2026-10-15

- La scheda elettronica entra in questa fase? → A: no, sarà la fase 7 (`007-schede`). La spec di progetto elenca "documenti, upload, scheda elettronica, liberatoria" in una sola feature, ma sono due blocchi indipendenti (file e questionario) e due PR più piccole si rivedono meglio. Si può unire se Mahima preferisce.
- Dove stanno i file (S3/MinIO nella spec di progetto)? → A (scelta di default, da confermare): in MongoDB (GridFS) dietro un'interfaccia `DocumentStorage`. Nel locale non serve un altro container e i file sono piccoli (≤ 10 MB); passare a MinIO vorrà dire scrivere una seconda implementazione.
- Antivirus (FR-048, NFR-08)? → A (scelta di default): non in questa fase, nel locale non c'è un motore; si controlla invece il tipo reale del file dai primi byte (JPEG, PNG, PDF), non dal nome. L'antivirus resta nella checklist della spec di progetto.
- Quali formati e dimensioni valgono se l'evento non li indica? → A (scelta di default): JPEG, PNG o PDF, fino a 5 MB; l'evento può restringere o ampliare fino a 10 MB.
- Chi verifica i documenti? → A: chi gestisce l'evento con la regola territoriale della fase 2 (stessa regola dei pagamenti).
- Un'iscrizione con documenti in verifica può scadere? → A (scelta di default): no. Se resta solo qualcosa che spetta alla segreteria, la scadenza e i promemoria non partono: il socio non deve pagare il ritardo di chi verifica.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - L'evento dice quali documenti servono (Priority: P1)

Chi crea l'evento indica i documenti richiesti: per ciascuno un codice, una descrizione, se si carica online o è cartaceo e, se online, formati ammessi e dimensione massima (es. "foto, JPEG, fino a 1 MB"). Il socio che si iscrive vede nell'iscrizione un adempimento per ogni documento, con i vincoli.

**Why this priority**: senza vincoli noti il socio non sa cosa caricare e il controllo (storia 2) non ha regole.

**Independent Test**: creare un evento con due documenti (foto online, liberatoria cartacea), iscriversi e leggere due adempimenti distinti con i vincoli.

**Acceptance Scenarios**:

1. **Given** un evento con documenti "foto" (online, JPEG, ≤ 1 MB) e "liberatoria" (cartacea), **When** un socio si iscrive, **Then** l'iscrizione ha due adempimenti documento da fare, ciascuno col suo codice, tipo (online o cartaceo) e vincoli.
2. **Given** un evento con un documento senza vincoli, **When** si legge, **Then** valgono i default (JPEG, PNG, PDF, ≤ 5 MB).
3. **Given** due documenti con lo stesso codice, o una dimensione oltre 10 MB, **When** la segreteria salva l'evento, **Then** è rifiutato con i campi da correggere.
4. **Given** un evento della fase 2 con un solo adempimento documento senza codice, **When** un socio si iscrive, **Then** funziona come prima, con i default.

---

### User Story 2 - Il socio carica un documento (Priority: P1)

Il socio, o il suo presentatore, carica un file per un adempimento documento online. Il sistema controlla dimensione e tipo reale; se va bene l'adempimento passa "in verifica". Caricare di nuovo sostituisce il file precedente (che resta nel registro) finché non è stato validato.

**Why this priority**: è l'azione del socio che manda avanti l'iscrizione.

**Independent Test**: iscriversi, caricare una foto JPEG valida e leggere l'adempimento "in verifica".

**Acceptance Scenarios**:

1. **Given** un adempimento documento da fare, **When** il socio carica un JPEG entro la dimensione, **Then** il documento è registrato e l'adempimento è in verifica.
2. **Given** un file con estensione `.jpg` che in realtà è un altro tipo, o un formato non ammesso, o oltre la dimensione, **When** si carica, **Then** è rifiutato con il campo `file` e il motivo.
3. **Given** un documento già in verifica o rifiutato, **When** si carica un nuovo file, **Then** il precedente passa a "sostituito" e il nuovo è in verifica.
4. **Given** un documento già validato, **When** si prova a caricare, **Then** è rifiutato (409).
5. **Given** un adempimento cartaceo, **When** si prova a caricare, **Then** è rifiutato (409): lo segna ricevuto la segreteria.
6. **Given** un'iscrizione chiusa, **When** si prova a caricare, **Then** è rifiutato (409).
7. **Given** una persona che non è il partecipante né il presentatore, **When** carica, **Then** riceve 404.

---

### User Story 3 - La segreteria valida o rifiuta (Priority: P1)

La segreteria che gestisce l'evento vede i documenti in verifica, li scarica, e per ciascuno li valida (l'adempimento è soddisfatto; se non resta altro l'iscrizione si completa) o li rifiuta con un motivo (l'adempimento torna da fare e il socio riceve un'e-mail col motivo). I documenti cartacei li segna ricevuti con l'azione già esistente.

**Why this priority**: senza verifica i documenti restano "in verifica" per sempre.

**Independent Test**: caricare un documento, validarlo con la segreteria e vedere l'iscrizione completa; ripetere rifiutandolo e leggere l'e-mail con il motivo.

**Acceptance Scenarios**:

1. **Given** un documento in verifica, **When** la segreteria lo valida, **Then** l'adempimento è soddisfatto e, se non resta altro, l'iscrizione è completa (e-mail di completamento della fase 4).
2. **Given** un documento in verifica, **When** la segreteria lo rifiuta con un motivo, **Then** il documento è rifiutato, l'adempimento è di nuovo da fare con il motivo, il socio e il presentatore ricevono un'e-mail con il motivo.
3. **Given** un rifiuto senza motivo, **Then** è rifiutato con 422.
4. **Given** una segreteria di un'altra regione, o un socio, **When** valida o rifiuta, **Then** riceve 403.
5. **Given** un documento già validato o rifiutato, **When** si prova a validare o rifiutare, **Then** è un conflitto (409).
6. **Given** un'iscrizione completa, **When** la segreteria rifiuta un documento già validato, **Then** non è possibile (409): per correggere si usa la procedura d'ufficio.

---

### User Story 4 - I file li vedono solo gli aventi diritto (Priority: P1)

Elenco e scarico dei documenti di un'iscrizione sono riservati al partecipante, al suo presentatore e alla segreteria che gestisce l'evento. Per tutti gli altri l'iscrizione non esiste (404).

**Why this priority**: sono foto e liberatorie di minorenni.

**Independent Test**: caricare un documento e provare a scaricarlo con il socio, con la segreteria giusta, con quella di un'altra regione e con un altro socio.

**Acceptance Scenarios**:

1. **Given** un documento caricato, **When** lo scaricano partecipante, presentatore o la segreteria dell'evento, **Then** ricevono il file con nome e tipo originali.
2. **Given** lo stesso documento, **When** lo chiede un altro socio o una segreteria di un'altra regione, **Then** ricevono 404.
3. **Given** un documento sostituito o rifiutato, **When** lo scarica la segreteria, **Then** è ancora disponibile (registro).

---

### User Story 5 - Verifica ordinata e coda della segreteria (Priority: P2)

La segreteria vede l'elenco dei documenti in verifica della propria regione, filtrabile per evento, per decidere cosa lavorare.

**Why this priority**: comodo, ma la verifica funziona anche conoscendo l'iscrizione.

**Independent Test**: caricare tre documenti su due eventi e leggere la coda filtrata.

**Acceptance Scenarios**:

1. **Given** documenti in verifica su eventi di due regioni, **When** la segreteria PIE legge la coda, **Then** vede solo quelli PIE (l'amministrazione tutti).
2. **Given** un filtro per evento o per stato, **Then** la coda lo applica.

---

### Edge Cases

- Un documento in verifica e l'evento annullato o l'iscrizione ritirata: il documento resta nel registro, l'adempimento non è più lavorabile.
- Due caricamenti contemporanei dello stesso adempimento: vince l'ultimo, il primo passa a sostituito; non restano due documenti "in verifica" per lo stesso adempimento.
- Due segreterie che validano o rifiutano insieme: vince la prima, l'altra riceve 409.
- File vuoto, nome con percorso o caratteri strani: il nome si normalizza (solo il nome, senza percorso), il file vuoto è rifiutato.
- Una promozione dalla lista d'attesa con documenti già caricati: restano validi.
- Un adempimento rifiutato scade con l'iscrizione: la scadenza dell'iscrizione vale come sempre.

## Requirements *(mandatory)*

### Documenti richiesti dall'evento

- **FR-001**: Un adempimento di tipo documento dell'evento MUST poter indicare un codice (univoco nell'evento), una descrizione, `ONLINE` o `CARTACEO` e, per l'online, formati ammessi e dimensione massima (≤ 10 MB). Senza indicazioni valgono i default (JPEG, PNG, PDF, 5 MB) e `ONLINE`.
- **FR-002**: Un'iscrizione MUST avere un adempimento per ogni documento richiesto, con codice, tipo e vincoli copiati dall'evento. Gli eventi esistenti con un solo documento senza codice MUST continuare a funzionare.
- **FR-003**: Codici duplicati o dimensioni fuori limite nell'evento MUST essere rifiutati con i campi da correggere.

### Caricamento

- **FR-004**: Il partecipante o il suo presentatore MUST poter caricare un file per un adempimento documento online di un'iscrizione attiva.
- **FR-005**: Il sistema MUST rifiutare file vuoti, oltre la dimensione ammessa o di un tipo non ammesso, riconoscendo il tipo reale dai primi byte e non dal nome o dal tipo dichiarato; l'errore indica il campo `file` e il motivo.
- **FR-006**: Un caricamento valido MUST registrare il documento (nome normalizzato, tipo rilevato, dimensione, impronta SHA-256, chi e quando) e portare l'adempimento "in verifica".
- **FR-007**: Un nuovo caricamento MUST sostituire il precedente non validato, che resta nel registro come sostituito; dopo la validazione il caricamento MUST essere rifiutato (409), come per gli adempimenti cartacei e le iscrizioni chiuse.

### Verifica

- **FR-008**: La segreteria che gestisce l'evento MUST poter validare un documento in verifica: l'adempimento è soddisfatto e, se non resta altro, l'iscrizione si completa.
- **FR-009**: La segreteria MUST poter rifiutare un documento in verifica con un motivo obbligatorio: l'adempimento torna da fare con il motivo e il partecipante e il presentatore ricevono un'e-mail.
- **FR-010**: Validare e rifiutare MUST essere atomici e mutuamente esclusivi: vince il primo, il secondo riceve 409.
- **FR-011**: I documenti cartacei MUST restare soddisfatti con l'azione esistente della segreteria (fase 3).
- **FR-012**: Un'iscrizione dove restano solo adempimenti "in verifica" MUST NON scadere e MUST NON ricevere promemoria di scadenza.

### Visibilità

- **FR-013**: Elenco e scarico dei documenti di un'iscrizione MUST essere riservati a partecipante, presentatore e segreteria che gestisce l'evento; per gli altri 404. I documenti sostituiti e rifiutati restano scaricabili da questi aventi diritto.
- **FR-014**: La segreteria MUST poter leggere la coda dei documenti in verifica della propria regione (tutta per l'amministrazione), filtrabile per evento e per stato.

### Errori

- **FR-015**: Gli errori MUST usare l'envelope esistente: file non valido `42200` con campo `file`; azione non ammessa sullo stato del documento o dell'adempimento `40960` (dominio `DOCUMENT`) o `40910`; permesso mancante `40300`; risorsa non visibile `40400`.

### Key Entities

- **Obligation / EnrollmentObligation** (esistenti): per il tipo documento guadagnano codice, tipo online/cartaceo e vincoli; lo stato guadagna "in verifica".
- **Document**: un file caricato per un adempimento di un'iscrizione, con metadati, stato (caricato, validato, rifiutato, sostituito) ed esito della verifica. Il contenuto sta nell'archivio dei file.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un file con tipo reale non ammesso, vuoto o troppo grande non viene mai registrato, qualunque nome o tipo dichiarato abbia (prova su almeno cinque casi).
- **SC-002**: In una sequenza qualsiasi di caricamenti, esiste al più un documento "caricato" per adempimento.
- **SC-003**: Nessun utente fuori dagli aventi diritto riesce a scaricare un documento (404) né a vederne l'esistenza.
- **SC-004**: Un'iscrizione con soli adempimenti in verifica non scade, anche dopo la scadenza.
- **SC-005**: Il rifiuto di un documento produce un'e-mail con il motivo e riporta l'adempimento da fare.

## Assumptions

- Le fasi 1-5 sono unite in `feature`; i pagamenti sono solo bonifico.
- Le e-mail passano dall'outbox e da Kafka della fase 4.
- La verifica usa lo stesso entitlement delle azioni d'ufficio sull'iscrizione e la regola territoriale della fase 2.
- Nome del file originale conservato solo come testo di visualizzazione; il file si identifica con un id.
- Fuori ambito: scheda elettronica e liberatoria online, antivirus, MinIO, anteprime, ricevute di bonifico (non servono: i bonifici li registra la segreteria), conservazione e cancellazione dei file per privacy (checklist della spec di progetto).
