# Feature Specification: Notifiche e-mail

**Feature Branch**: `004-notifiche`

**Created**: 2026-10-14

**Status**: Draft

**Input**: User description: "Fase 4 – Notifiche e-mail via Kafka (spec di progetto v2.0: FR-028, FR-056…FR-059, §2.6.4, NFR-06; decisione di Mahima del 14/10: Kafka resta anche con l'applicazione unica). Ogni cambio di stato rilevante di un'iscrizione o di un evento produce un evento di dominio pubblicato su Kafka senza perderlo (outbox); un consumer idempotente sceglie destinatari e modello e invia l'e-mail (Mailpit in locale), con ritentativi e dead letter; ogni invio è registrato (Notifica). E-mail: conferma di registrazione, avviso al presentatore, richiesta di autorizzazione ai capi con link firmato monouso, promemoria 3 e 1 giorno prima della scadenza, completamento, scadenza, ritiro, promozione, rifiuto, annullamento per posti esauriti, modifica di date o luogo e annullamento dell'evento. Chi non ha e-mail non riceve nulla ma il registro lo segna. Fuori ambito: rimborsi e pagamenti effettivi (fase 5), candidature, push e SMS, Keycloak, preferenze di notifica."

## Clarifications

### Session 2026-10-14

- Kafka resta il canale degli eventi di dominio anche con l'applicazione unica (ADR-003)? → A: sì, decisione di Mahima del 14/10.
- Chi è in lista d'attesa paga solo alla promozione (FR-017 della 003)? → A: sì, confermato da Mahima il 14/10; la mail di promozione chiede il pagamento.
- A chi serve il link firmato (FR-028 di progetto)? → A (scelta di default): ai capi che devono autorizzare. La validazione del presentatore nella fase 3 è già soddisfatta quando il capo iscrive il ragazzo, quindi non resta nessun presentatore da far validare via link. Il link serve al capo unità e al capo gruppo per autorizzare o negare senza entrare nel sito.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Sapere subito com'è andata l'iscrizione (Priority: P1)

Chi si iscrive riceve un'e-mail di conferma con il numero dell'iscrizione, l'evento, la posizione (nei posti o in attesa, con il numero in lista), gli adempimenti da completare e la scadenza. Se l'ha iscritto il capo, anche il capo riceve un avviso.

**Why this priority**: è la prima cosa che un socio si aspetta dopo l'iscrizione; senza conferma scritta chiama la segreteria. Dimostra da sola tutto il percorso: evento di dominio, coda, invio, registro.

**Independent Test**: iscrivere un rover a un evento e verificare nella casella di prova un'unica e-mail con i dati giusti; iscrivere un esploratore tramite il capo e verificare le due e-mail (ragazzo e capo).

**Acceptance Scenarios**:

1. **Given** un'iscrizione nei posti, **When** viene registrata, **Then** il partecipante riceve una sola e-mail di conferma con numero, evento, "nei posti", adempimenti da fare e scadenza.
2. **Given** un'iscrizione in lista d'attesa, **When** viene registrata, **Then** l'e-mail dice "in attesa", con la posizione in lista, e non chiede il pagamento.
3. **Given** un capo che iscrive un ragazzo della sua unità, **When** l'iscrizione è registrata, **Then** il ragazzo riceve la conferma e il capo un avviso con il numero dell'iscrizione.
4. **Given** un partecipante senza indirizzo e-mail, **When** l'iscrizione è registrata, **Then** non parte nessuna e-mail e il registro delle notifiche indica "nessun indirizzo".

---

### User Story 2 - Autorizzare dal link nell'e-mail (Priority: P1)

Quando un'iscrizione ha bisogno dell'autorizzazione del capo unità o del capo gruppo del partecipante, ognuno di loro riceve un'e-mail con un link personale. Aprendo il link vede l'iscrizione e può autorizzare o negare (con motivo) senza entrare nel sito. Il link vale una volta sola e smette di valere quando l'iscrizione non è più attiva o l'autorizzazione è già stata data.

**Why this priority**: le autorizzazioni dei capi sono il passo che più spesso blocca il completamento entro i 9 giorni; il link toglie il passaggio dal login.

**Independent Test**: iscrivere un rover a un evento con l'autorizzazione del capo clan; aprire il link arrivato al capo, autorizzare e verificare l'adempimento soddisfatto; riaprire lo stesso link e verificare che non vale più.

**Acceptance Scenarios**:

1. **Given** un'iscrizione con l'autorizzazione del capo unità da dare, **When** viene registrata, **Then** ogni capo con servizio nell'unità del partecipante riceve un'e-mail con un link personale.
2. **Given** un link valido, **When** il capo lo apre, **Then** vede evento, partecipante e la domanda, senza che nulla cambi finché non conferma.
3. **Given** un link valido, **When** il capo autorizza, **Then** l'adempimento è soddisfatto come se l'avesse fatto dal sito, e il link non vale più.
4. **Given** un link valido, **When** il capo nega con un motivo, **Then** l'iscrizione è rifiutata come dal sito; senza motivo il diniego non è accettato.
5. **Given** un link già usato, scaduto, alterato o di un'iscrizione non più attiva, **When** viene aperto, **Then** il sistema risponde che il link non è più valido, senza dire nulla dell'iscrizione.
6. **Given** un'autorizzazione già data dal sito, **When** il capo apre il link ricevuto prima, **Then** il link non è più valido.

---

### User Story 3 - Non dimenticare la scadenza (Priority: P2)

Chi ha un'iscrizione ancora da completare riceve un promemoria 3 giorni e 1 giorno prima della scadenza, con l'elenco di ciò che manca. Chi è in attesa riceve i promemoria per gli adempimenti che lo riguardano.

**Why this priority**: riduce le iscrizioni scadute (e quindi i posti persi) senza lavoro della segreteria.

**Independent Test**: con l'orologio spostato a 3 giorni e poi a 1 giorno dalla scadenza, verificare un solo promemoria per ciascun momento; per un'iscrizione completa nessun promemoria.

**Acceptance Scenarios**:

1. **Given** un'iscrizione registrata con adempimenti da fare, **When** mancano 3 giorni alla scadenza, **Then** il partecipante riceve un promemoria con gli adempimenti mancanti e la data di scadenza.
2. **Given** la stessa iscrizione, **When** manca 1 giorno, **Then** riceve il secondo promemoria; nessun promemoria è inviato due volte.
3. **Given** un'iscrizione completa, ritirata o scaduta, **When** arriva il momento del promemoria, **Then** non parte nulla.
4. **Given** un'iscrizione promossa con una nuova scadenza, **When** si avvicina la nuova scadenza, **Then** i promemoria seguono la nuova scadenza.

---

### User Story 4 - Essere avvisati quando l'iscrizione cambia (Priority: P2)

Il partecipante (e il presentatore, se c'è) riceve un'e-mail quando l'iscrizione diventa completa, scade, viene ritirata (con l'indicazione del diritto al rimborso), viene promossa dalla lista d'attesa (con il pagamento da fare e la nuova scadenza), viene rifiutata (con il motivo) o annullata perché i posti sono esauriti alla partenza.

**Why this priority**: sono i momenti in cui il socio deve fare qualcosa o sapere che il posto è perso; la promozione in particolare apre una nuova scadenza.

**Independent Test**: portare un'iscrizione in ciascuno degli stati e verificare un'e-mail per ciascun passaggio con il contenuto giusto.

**Acceptance Scenarios**:

1. **Given** un'iscrizione in attesa, **When** viene promossa, **Then** il partecipante riceve un'e-mail con "nei posti", il pagamento da fare (se la quota è maggiore di 0) e la nuova scadenza.
2. **Given** un ritiro almeno 7 giorni prima della partenza, **When** è registrato, **Then** l'e-mail dice che il rimborso spetta; altrimenti dice che non spetta.
3. **Given** un'autorizzazione negata, **When** l'iscrizione diventa rifiutata, **Then** l'e-mail riporta il motivo.
4. **Given** un'iscrizione completata, scaduta o annullata per posti esauriti, **When** succede, **Then** parte l'e-mail corrispondente.

---

### User Story 5 - Essere avvisati quando l'evento cambia (Priority: P3)

Se la segreteria cambia le date o il luogo di un evento pubblicato, o lo annulla, tutti gli iscritti attivi (nei posti e in attesa) ricevono un'e-mail; per l'annullamento con il motivo e il diritto al rimborso.

**Why this priority**: riguarda meno casi ma, quando succede, tocca tutti gli iscritti in una volta.

**Independent Test**: con un evento con tre iscritti, cambiare il luogo e verificare tre e-mail; annullarlo e verificare tre e-mail di annullamento.

**Acceptance Scenarios**:

1. **Given** un evento con iscritti attivi, **When** la segreteria cambia data di partenza, data di rientro o luogo, **Then** ogni iscritto attivo riceve un'e-mail con il nuovo valore.
2. **Given** una modifica che non tocca date né luogo (es. descrizione, posti), **When** è salvata, **Then** non parte nessuna e-mail per la modifica.
3. **Given** un evento annullato, **When** l'annullamento è registrato, **Then** ogni iscritto che era attivo riceve un'e-mail con il motivo e con il diritto al rimborso (sì per chi era nei posti con quota maggiore di 0).

---

### User Story 6 - Nessuna e-mail persa, nessuna doppia (Priority: P2)

Il sistema non perde un avviso se il canale degli eventi o il server di posta non rispondono per un po': ritenta e, dopo l'ultimo tentativo, mette l'avviso tra quelli falliti, visibili a chi amministra. Lo stesso avviso ricevuto due volte non produce due e-mail.

**Why this priority**: è la garanzia richiesta da NFR-06 di progetto ("nessun evento di dominio perso"); senza, le storie precedenti non sono affidabili.

**Independent Test**: fermare il server di posta, iscrivere un socio, riavviarlo e verificare che l'e-mail arrivi una sola volta; ripubblicare lo stesso avviso e verificare che non parta una seconda e-mail.

**Acceptance Scenarios**:

1. **Given** il canale degli eventi non disponibile, **When** un'iscrizione cambia stato, **Then** il cambio di stato riesce lo stesso e l'avviso parte quando il canale torna disponibile.
2. **Given** il server di posta non disponibile, **When** arriva un avviso, **Then** il sistema ritenta; se torna disponibile entro i tentativi l'e-mail parte una volta sola.
3. **Given** tentativi esauriti, **When** l'ultimo fallisce, **Then** l'avviso è registrato come fallito e finisce tra quelli da esaminare.
4. **Given** lo stesso avviso consegnato due volte, **When** viene elaborato, **Then** parte una sola e-mail per destinatario.
5. **Given** l'amministrazione, **When** consulta il registro delle notifiche di un'iscrizione, **Then** vede ogni avviso con destinatario, modello, stato (inviata, fallita, nessun indirizzo) e data.

---

### Edge Cases

- Partecipante e presentatore con lo stesso indirizzo e-mail: ricevono comunque l'e-mail pensata per ciascun ruolo (il registro le distingue).
- Più capi con servizio nella stessa unità: ricevono ognuno il proprio link; quando uno autorizza, i link degli altri non valgono più.
- Nessun capo unità o capo gruppo trovato: nessuna e-mail di autorizzazione, il registro segna "nessun destinatario"; l'autorizzazione resta possibile dal sito o dalla segreteria.
- Il capo gruppo è anche capo dell'unità del partecipante: riceve due e-mail distinte, una per ciascuna autorizzazione.
- Il capo che apre il link è anche il partecipante (si è iscritto lui): non riceve il link per la propria iscrizione.
- Promemoria quando la scadenza cade di notte o a cavallo del cambio dell'ora legale: "3 giorni prima" si conta in giorni di calendario all'ora italiana.
- Iscrizione scaduta e subito dopo il posto passa al primo in attesa: partono sia l'e-mail di scadenza sia quella di promozione, ai rispettivi partecipanti.
- Modifica di date o luogo seguita subito da un'altra: ogni modifica produce il suo avviso, con i valori di quel momento.
- Link aperto dopo la scadenza dell'iscrizione ma prima che il job la segni scaduta: il link non vale, perché la scadenza è passata.

## Requirements *(mandatory)*

### Eventi di dominio

- **FR-001**: Ogni cambio di stato rilevante (iscrizione registrata, autorizzazione richiesta, completata, scaduta, ritirata, promossa, rifiutata, annullata per posti esauriti; evento modificato in date o luogo, evento annullato) MUST produrre un evento di dominio pubblicato sul canale degli eventi (FR-059 di progetto, §2.6.4).
- **FR-002**: Il cambio di stato e la registrazione dell'evento di dominio da pubblicare MUST avvenire insieme: se uno fallisce, fallisce anche l'altro; un evento registrato MUST essere pubblicato prima o poi, anche se il canale era fermo al momento del cambio (NFR-06 di progetto).
- **FR-003**: Ogni evento di dominio MUST avere un identificativo unico, il tipo, l'istante e i dati necessari a comporre le e-mail senza rileggere lo stato attuale (che nel frattempo può essere cambiato).

### Invio

- **FR-004**: Per ogni evento di dominio il sistema MUST individuare i destinatari secondo la tabella delle notifiche (sotto) e inviare a ciascuno al più una e-mail per evento (consumer idempotente).
- **FR-005**: Se l'invio fallisce il sistema MUST ritentare con attese crescenti; esauriti i tentativi l'avviso MUST finire tra quelli falliti da esaminare e il registro lo MUST segnare come fallito.
- **FR-006**: Ogni avviso (anche non inviato) MUST essere registrato come Notifica con destinatario, modello, evento di origine, iscrizione o evento di riferimento, stato (`SENT`, `FAILED`, `NO_ADDRESS`, `NO_RECIPIENT`) e istante.
- **FR-007**: L'amministrazione e chi gestisce l'evento MUST poter consultare il registro delle notifiche di un'iscrizione.

### Contenuti (tabella delle notifiche)

- **FR-008**: Registrazione → partecipante: conferma con numero, evento, posizione (con la posizione in lista se in attesa), adempimenti da fare e scadenza (FR-056 di progetto); presentatore, se c'è: avviso con il numero.
- **FR-009**: Autorizzazione richiesta → ogni capo con servizio nell'unità del partecipante (per l'autorizzazione del capo unità) e ogni capo gruppo del gruppo del partecipante (per quella del capo gruppo), escluso il partecipante stesso: e-mail con link personale (FR-057, FR-028 di progetto).
- **FR-010**: Promemoria → partecipante, 3 giorni e 1 giorno di calendario prima della scadenza, solo se l'iscrizione è ancora registrata con adempimenti da fare; ogni promemoria al più una volta per scadenza (FR-058 di progetto).
- **FR-011**: Completamento, scadenza, ritiro (con l'indicazione del diritto al rimborso), promozione (con il pagamento da fare se la quota è maggiore di 0 e la nuova scadenza), rifiuto (con il motivo), annullamento per posti esauriti → partecipante e presentatore, se c'è.
- **FR-012**: Modifica della data di partenza, della data di rientro o del luogo di un evento pubblicato → ogni iscritto attivo, con i valori nuovi; altre modifiche non producono avvisi.
- **FR-013**: Annullamento dell'evento → ogni iscritto che era attivo, con il motivo e il diritto al rimborso.
- **FR-014**: Le e-mail MUST essere in italiano, con date nell'ora italiana, e contenere il numero dell'iscrizione o il codice dell'evento per ritrovarli.

### Link firmato

- **FR-015**: Il link di autorizzazione MUST essere personale (legato a capo, iscrizione e tipo di autorizzazione), impossibile da indovinare o alterare senza che il sistema se ne accorga.
- **FR-016**: Aprire il link MUST mostrare l'iscrizione e la domanda senza cambiare nulla; l'autorizzazione o il diniego (con motivo obbligatorio) MUST richiedere una conferma esplicita.
- **FR-017**: Il link MUST valere una sola volta e MUST smettere di valere quando: è già stato usato, l'autorizzazione non è più da dare (data da un altro capo, dal sito o dalla segreteria), l'iscrizione non è più attiva o la sua scadenza è passata.
- **FR-018**: Un link non valido MUST ricevere sempre la stessa risposta ("link non più valido"), senza rivelare se l'iscrizione esiste.
- **FR-019**: Un'azione fatta dal link MUST avere lo stesso effetto e gli stessi avvisi della stessa azione fatta dal sito (FR-009 della 003).

### Errori

- **FR-020**: Gli errori delle nuove funzioni MUST usare l'envelope e i codici delle fasi precedenti, con status HTTP significativo: 404 per link non valido, 409 per azione non più possibile, 422 per motivo mancante.

### Key Entities

- **Evento di dominio**: fatto accaduto (tipo, istante, identificativo unico, dati dell'iscrizione o dell'evento al momento del fatto); prima registrato, poi pubblicato.
- **Notifica**: un avviso a un destinatario per un evento di dominio; destinatario, indirizzo, modello, stato, tentativi, istante, riferimento all'iscrizione o all'evento.
- **Link di autorizzazione**: permesso monouso per un capo su una autorizzazione di una iscrizione; scade con l'iscrizione.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In locale, l'e-mail di conferma arriva nella casella di prova entro 10 secondi dall'iscrizione.
- **SC-002**: Nei test, ogni cambio di stato della tabella delle notifiche produce esattamente un avviso per destinatario (nessuno mancante, nessun doppione), anche consegnando due volte lo stesso evento.
- **SC-003**: Con il canale degli eventi fermo durante 20 iscrizioni, alla ripresa partono tutte le 20 conferme, una per iscrizione.
- **SC-004**: Un capo autorizza un'iscrizione dal link in meno di 30 secondi e senza login; il secondo uso dello stesso link è rifiutato nel 100% dei casi.
- **SC-005**: Nessun promemoria parte per iscrizioni complete, ritirate, scadute o annullate.

## Assumptions

- Il canale degli eventi è Kafka (decisione di Mahima del 14/10); in locale l'invio passa da Mailpit, già nel docker-compose.
- Gli indirizzi e-mail sono quelli dell'utente (`User.email`); i capi si trovano tra gli utenti censiti con la stessa unità (capo unità) o con il flag di capo gruppo nello stesso gruppo, come nella fase 3.
- Il link porta a una pagina del frontend, il cui indirizzo base è configurabile; il backend espone le API per leggere e usare il link.
- I promemoria li produce il job della fase 3 (stesso giro di 5 minuti), che a sua volta pubblica un evento di dominio.
- Il registro delle notifiche si consulta via API; una pagina di amministrazione per gli avvisi falliti e il loro rinvio manuale non è in questa fase.
- Il rimborso effettivo, le e-mail di pagamento e rimborso eseguito restano della fase 5; le candidature e i loro esiti di una fase successiva.
- Canali push e SMS e le preferenze di notifica non sono in questa fase.
