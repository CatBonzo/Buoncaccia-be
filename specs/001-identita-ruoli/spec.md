# Feature Specification: Identità, ruoli e autenticazione mock

**Feature Branch**: `001-identita-ruoli`

**Created**: 2026-10-11

**Status**: Draft

**Input**: User description: "Fase 1 del planner: identità e auth mock. Utenti di prova in `mocked-identity.yml`, token generati dal backend all'avvio e visibili solo nell'elenco degli utenti di prova, controllo degli entitlement del token, envelope di risposta unico, profilo personale. Tre ruoli: amministrazione, segreteria, user. Fonte: Note Tecniche Backend v2.0, par. 2.2, 2.6.1, 2.6.3, FR-001…FR-006, AUTH-1…AUTH-5."

## Clarifications

### Session 2026-10-11

- Q15: Quali valori hanno ruoli ed entitlement dell'MVP? → A: quelli proposti nel planner, confermati da Mahima (vedi FR-016).

### Session 2026-10-12

- Q14: Status HTTP degli errori significativo o sempre 200? → A: significativo (401, 403, 404, 409, 422, 500), sempre con l'envelope nel corpo; scelto da Mahima (vedi FR-015).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ottenere un token di prova (Priority: P1)

Chi sviluppa o prova l'applicazione (anche il frontend) avvia il backend in modalità mock e, con una sola richiesta, ottiene l'elenco degli utenti di prova, ciascuno con i suoi dati, ruoli, entitlement e un token già pronto da usare. Non c'è una schermata di login.

**Why this priority**: senza un token nessun'altra funzione protetta si può provare; è il prerequisito di tutte le fasi successive e del lavoro sul frontend.

**Independent Test**: avviare il backend in modalità mock, chiedere l'elenco degli utenti di prova e verificare che ogni voce abbia un token che il backend stesso accetta.

**Acceptance Scenarios**:

1. **Given** il backend avviato in modalità mock con quattro utenti di prova nel file, **When** si chiede l'elenco degli utenti di prova, **Then** la risposta contiene quattro voci, ognuna con identificativi, nome, ruoli, entitlement, emittente `mock-auth` e un token.
2. **Given** un token preso dall'elenco, **When** lo si usa per una richiesta protetta, **Then** la richiesta è accettata come fatta da quell'utente.
3. **Given** il backend riavviato, **When** si usa un token ottenuto prima del riavvio, **Then** la richiesta è rifiutata come non autenticata, e l'elenco restituisce token nuovi.
4. **Given** il backend avviato senza modalità mock, **When** si chiede l'elenco degli utenti di prova, **Then** la risorsa non esiste.

---

### User Story 2 - Vedere il proprio profilo (Priority: P1)

Una persona autenticata vede il proprio profilo: dati anagrafici, dati di censimento se è socia AGESCI, ruoli ed entitlement effettivi. Il frontend usa il profilo per decidere che cosa mostrare (ruolo) e quali azioni abilitare (entitlement).

**Why this priority**: è il primo uso reale del token e il punto da cui il frontend costruisce menu e permessi (FR-002 della spec di progetto).

**Independent Test**: con il token di ciascun utente di prova, chiedere il profilo e confrontarlo con la voce del file degli utenti di prova.

**Acceptance Scenarios**:

1. **Given** il token dell'utente `segreteria`, **When** chiede il proprio profilo, **Then** vede il ruolo di segreteria e i suoi entitlement, e nessun dato di un altro utente.
2. **Given** il token dell'utente `socio`, **When** chiede il proprio profilo, **Then** vede i propri dati di censimento (codice socio, gruppo, branca, stato in regola).
3. **Given** il token dell'utente `esterno`, che non è socio AGESCI, **When** chiede il proprio profilo, **Then** il censimento è vuoto e il profilo indica che la persona non è socia.
4. **Given** una richiesta del profilo senza token, **When** arriva al backend, **Then** è rifiutata come non autenticata.

---

### User Story 3 - Operazioni consentite solo a chi ha l'entitlement (Priority: P2)

Ogni operazione protetta dichiara l'entitlement che serve. Il backend la consente solo se quell'entitlement è nel token, senza limiti territoriali e senza un catalogo di permessi suo.

**Why this priority**: è la regola di autorizzazione (AUTH-1) che tutte le feature successive riusano; va provata subito su un'operazione di esempio.

**Independent Test**: un'operazione di prova protetta da un entitlement di segreteria, chiamata con i token dei quattro utenti di prova.

**Acceptance Scenarios**:

1. **Given** un'operazione che richiede l'entitlement di gestione eventi, **When** la chiama `segreteria` o `amministratore`, **Then** è consentita.
2. **Given** la stessa operazione, **When** la chiama `socio`, **Then** è rifiutata per mancanza di permesso, con un messaggio che lo dice.
3. **Given** un token con firma alterata, emittente sconosciuto o scaduto, **When** viene usato, **Then** la richiesta è rifiutata come non autenticata.

---

### User Story 4 - Risposte sempre nella stessa forma (Priority: P2)

Ogni risposta del backend, di successo o di errore, ha la stessa forma: codice di esito, messaggio, dominio dell'errore e contenuto. Il frontend legge sempre gli stessi quattro campi.

**Why this priority**: il frontend e i test di tutte le feature dipendono da questa forma; cambiarla dopo costerebbe molto.

**Independent Test**: confrontare la forma delle risposte di successo, di non autenticazione, di permesso mancante, di risorsa inesistente e di errore imprevisto.

**Acceptance Scenarios**:

1. **Given** una richiesta riuscita, **When** arriva la risposta, **Then** il codice di esito è 0, il messaggio è vuoto o di conferma e i dati sono nel contenuto.
2. **Given** una richiesta rifiutata o fallita, **When** arriva la risposta, **Then** il codice di esito è diverso da 0, il messaggio spiega il problema in italiano, il dominio indica l'area (es. autenticazione) e il contenuto è vuoto.
3. **Given** un errore imprevisto del backend, **When** arriva la risposta, **Then** ha la stessa forma e non mostra dettagli interni (stack trace, nomi di classi).

### Edge Cases

- Il file degli utenti di prova manca, è vuoto o non è leggibile in modalità mock: il backend non parte e dice quale file e quale riga sono il problema.
- Due voci del file hanno lo stesso identificativo AGESCI: il backend non parte e lo segnala.
- Una voce indica un identificativo Buona Caccia fisso (es. `user-001`): quello viene usato come identificativo dell'utente, stabile tra i riavvii.
- Una voce viene tolta dal file: l'utente resta nel database ma non ha più un token; nessuna cancellazione automatica.
- Un utente cambia ruoli o entitlement nel file: al riavvio il nuovo token li riporta e il profilo li mostra.
- Un token valido appartiene a una persona non ancora presente nel database: al primo accesso l'utente viene creato dai claim del token.
- La richiesta usa uno schema diverso da `Bearer` o un token malformato: rifiutata come non autenticata, senza errore interno.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: In modalità mock il sistema MUST leggere all'avvio gli utenti di prova dal file `mocked-identity.yml` e creare o aggiornare ciascun utente usando l'identificativo AGESCI come chiave, così che l'identificativo Buona Caccia resti stabile tra i riavvii.
- **FR-002**: Se una voce indica un identificativo Buona Caccia, il sistema MUST usarlo come identificativo dell'utente.
- **FR-003**: A ogni avvio in modalità mock il sistema MUST generare una chiave di firma e un token firmato per ciascun utente di prova, tenendoli solo in memoria: i token non sono nel file né nel database e smettono di valere al riavvio.
- **FR-004**: In modalità mock il sistema MUST esporre l'elenco degli utenti di prova con, per ciascuno, i dati della voce, l'emittente e il token; fuori dalla modalità mock questa risorsa MUST NOT esistere.
- **FR-005**: In modalità mock il sistema MUST esporre la chiave pubblica con cui verificare i token.
- **FR-006**: Ogni token MUST contenere almeno: emittente, soggetto, data di emissione, scadenza, identificativo AGESCI, appartenenza (`isMemberOf`), ruoli (`role`) ed entitlement (`entitlement`). Il token mock MUST avere la stessa forma che avrà il token di Keycloak.
- **FR-007**: Ogni richiesta a una risorsa protetta MUST presentare un token valido (firma, emittente, scadenza); in caso contrario il sistema MUST rifiutarla come non autenticata.
- **FR-008**: Ogni operazione protetta MUST dichiarare l'entitlement richiesto; il sistema MUST consentirla solo se l'entitlement è nel token (AUTH-1), senza limiti territoriali, e MUST rifiutarla per mancanza di permesso altrimenti.
- **FR-009**: Il sistema MUST NOT avere un proprio catalogo di ruoli o permessi: ruoli, entitlement e il legame tra loro stanno nell'Identity Provider (nel mock, il file degli utenti di prova).
- **FR-010**: Una persona autenticata MUST poter vedere il proprio profilo: identificativi, nome, cognome, e-mail, dati di censimento se presenti, ruoli ed entitlement effettivi, e un'indicazione esplicita se è socia AGESCI.
- **FR-011**: A ogni accesso autenticato il sistema MUST aggiornare sull'utente la copia di ruoli ed entitlement ricevuti nel token; se l'utente non esiste ancora MUST crearlo dai claim del token.
- **FR-012**: Un utente senza dati di censimento MUST risultare non socio (AUTH-4), pur potendo avere ruoli ed entitlement.
- **FR-013**: Ogni risposta, di successo o di errore, MUST avere i campi `errorCode` (0 = successo), `errorMessage`, `errorDomain` e `payload`; gli errori MUST NOT mostrare dettagli interni.
- **FR-014**: Gli errori di questa feature MUST avere un codice proprio per: non autenticato, permesso mancante, risorsa inesistente, richiesta non valida, errore interno.
- **FR-015**: Lo status HTTP delle risposte di errore MUST essere significativo e coerente con `errorCode`: 401 (token assente, scaduto o non valido), 403 (entitlement mancante), 404 (risorsa inesistente), 409 (conflitto di stato), 422 (input non valido), 500 (errore imprevisto). Il corpo usa sempre l'envelope; le risposte di successo usano 200 (o 201 per una creazione).
- **FR-016**: I tre ruoli dell'MVP MUST avere i valori `agesci:sistema:amministratore`, `agesci:regione:segreteria`, `agesci:socio:user`. Gli entitlement (prefisso `agesci:entitlement:`) sono: `evento:gestione`, `iscrizione:gestione`, `pagamento:registrazione` (segreteria); `iscrizione:self` (user); `tipoevento:gestione` (solo amministrazione, che ha anche tutti gli altri). L'esempio `agesci:entitlement:agesci:censito:sede:segreteria` resta fuori finché non se ne conosce il significato.
- **FR-017**: Il passaggio dal token mock a Keycloak MUST richiedere solo un cambio di configurazione: nessuna regola di autorizzazione o di profilo può dipendere dal fatto che il token sia mock.

### Key Entities *(include if feature involves data)*

- **Utente**: la persona che usa il sistema. Identificativo Buona Caccia, identificativo AGESCI, e-mail, nome, cognome, stato, censimento (sotto-insieme facoltativo), copia di ruoli ed entitlement dell'ultimo token.
- **Censimento**: dati della persona come socia AGESCI: codice socio (univoco), gruppo, unità, branca, tipo socio (ragazzo/capo), stato e anno di censimento. Vuoto per chi non è socio.
- **Utente di prova**: voce del file `mocked-identity.yml`: identificativi, username, etichetta, nome, cognome, appartenenza, ruoli, entitlement, censimento facoltativo.
- **Token di accesso**: credenziale firmata che porta identità, ruoli ed entitlement; nel mock esiste solo in memoria fino al riavvio.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Dopo l'avvio in modalità mock, chi sviluppa ottiene un token funzionante con una sola richiesta, in meno di un minuto.
- **SC-002**: Il 100% delle risorse protette rifiuta le richieste senza token, con token alterato, scaduto o di un avvio precedente.
- **SC-003**: Il 100% delle risposte, comprese quelle di errore, ha i quattro campi dell'envelope.
- **SC-004**: I quattro utenti di prova coprono i tre ruoli dell'MVP più una persona non socia, e per ciascuno l'esito dell'operazione di prova (consentita o rifiutata) è quello atteso.
- **SC-005**: Il profilo di ogni utente di prova corrisponde al 100% alla sua voce nel file (ruoli, entitlement, censimento).
- **SC-006**: Nella fase 6 la stessa suite di test di questa feature passa con Keycloak cambiando solo la configurazione.

## Assumptions

- La modalità mock è il profilo `mocked` (nome scelto in `test-src/env/mocked.env`); la spec di progetto lo chiama `mock-auth`.
- Gli utenti di prova sono quattro: `amministratore` (user-001), `segreteria` (user-002), `socio` (user-003, censito e in regola), `esterno` (user-004, ruolo user ma non socio).
- Il token mock vale per tutta la vita del processo; la durata breve dei token riguarda Keycloak (fase 6).
- Uso solo locale: le risorse della modalità mock non vanno mai attivate fuori dallo sviluppo.
- I dati di censimento arrivano dal file degli utenti di prova; la sincronizzazione con il censimento AGESCI è fuori da questa feature.
- Fuori da questa feature: login con Keycloak (fase 6), gestione di ruoli ed entitlement da parte del backend, regione preferita (FR-008 di progetto), recupero e-mail (FR-007 di progetto).
- Riferimenti alle risorse API della spec di progetto (Appendice A): elenco utenti di prova `GET /api/v1/mock/auth/findAll`, chiave pubblica `GET /api/v1/mock/auth/jwks`, profilo `GET /api/v1/users/me`. Il contratto OpenAPI si definisce nel `plan`.
