# Feature Specification: Iscrizioni e lista d'attesa

**Feature Branch**: `003-iscrizioni`

**Created**: 2026-10-14

**Status**: Draft

**Input**: User description: "Fase 3 – Iscrizioni e lista d'attesa (spec di progetto v2.0: BR-01, BR-02, BR-04…BR-10, BR-15, FR-011, FR-021 parte iscrizioni, FR-023…FR-037, §2.5.2, §2.5.4, §2.5.5, §2.5.7, §2.6.6). Un socio R/S o capo si iscrive da solo a un evento con iscrizioni aperte di qualsiasi regione; un capo unità iscrive un ragazzo L/C o E/G della propria unità (capo presentatore). Controlli alla registrazione: finestra aperta, socio censito in regola, modalità di iscrizione dell'evento, requisiti BR-06, nessuna iscrizione attiva allo stesso evento, nessun vincolo di regione. Ordine di arrivo e posizione assegnati in modo atomico; promozione dalla lista d'attesa. Adempimenti generati dall'evento, scadenza a 9 giorni, scadenza automatica, passaggio a COMPLETA. Ritiro con calcolo del diritto al rimborso secondo BR-10 (decisione di Mahima del 14/10). Annullamento dell'evento che annulla le iscrizioni attive. Pagina di stato, le mie iscrizioni, iscrizioni presentate, elenco iscritti per la segreteria; contatore iscritti per l'indicatore. Fuori ambito: pagamenti e rimborsi effettivi (fase 5), notifiche e-mail e link firmati (fase 4), documenti e schede, candidature, Keycloak."

## Clarifications

### Session 2026-10-14

- Q12 (spec di progetto): finestra del rimborso, 3 giorni (US-10) o 7 giorni (BR-10)? → A: vale BR-10, almeno 7 giorni prima della partenza, data di partenza esclusa; decisa da Mahima (vedi FR-019). Il criterio di US-10 con 3 giorni è sbagliato e va corretto nella spec di progetto.
- Sigle delle regioni: restano quelle di tre lettere della fase 2 (PIE, LIG…); decisa da Mahima il 14/10.
- Chi è in lista d'attesa paga subito o alla promozione (FR-038 di progetto)? → A (proposta, da confermare): alla promozione, con una nuova scadenza di 9 giorni (vedi FR-017).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Iscriversi da soli a un evento di qualsiasi regione (Priority: P1)

Un socio rover/scolta o un capo, censito e in regola, apre un evento con le iscrizioni aperte (anche di un'altra regione) e si iscrive. Il sistema controlla che possa partecipare e gli assegna il suo ordine di arrivo: se ci sono ancora posti è "nei posti", altrimenti è "in attesa", con la sua posizione in lista.

**Why this priority**: è l'azione principale del sito e la ragione delle fasi precedenti. Senza di essa non ci sono iscritti, lista d'attesa né indicatore reale.

**Independent Test**: con un evento pubblicato e iscrizioni aperte, iscrivere un socio R/S idoneo e verificare l'iscrizione registrata, l'ordine di arrivo e la posizione; ripetere fino a superare i posti.

**Acceptance Scenarios**:

1. **Given** un evento con iscrizioni aperte, 30 posti e 29 iscrizioni attive, **When** un socio R/S idoneo si iscrive, **Then** l'iscrizione è REGISTRATA, nei posti, con ordine di arrivo 30, gli adempimenti dell'evento e la scadenza a 9 giorni.
2. **Given** lo stesso evento con 30 iscrizioni attive, **When** un altro socio idoneo si iscrive, **Then** l'iscrizione è REGISTRATA e in attesa, con posizione 1 in lista.
3. **Given** un evento della regione Piemonte, **When** un socio idoneo censito in Liguria si iscrive, **Then** l'iscrizione è registrata come per un socio piemontese.
4. **Given** un evento con limite "massimo 2 dello stesso gruppo" e 2 iscrizioni attive del gruppo, **When** un terzo socio del gruppo si iscrive, **Then** l'iscrizione è rifiutata con il motivo.
5. **Given** un socio con un'iscrizione attiva all'evento, **When** si iscrive di nuovo allo stesso evento, **Then** la nuova iscrizione è rifiutata come conflitto.
6. **Given** un evento con iscrizioni non ancora aperte, già chiuse, annullato o in bozza, **When** un socio prova a iscriversi, **Then** l'iscrizione è rifiutata come conflitto con lo stato dell'evento.
7. **Given** una persona non socia, un socio non in regola per l'anno in corso, o un lupetto/esploratore che prova a iscriversi da solo, **When** prova a iscriversi, **Then** l'iscrizione è rifiutata con il motivo.

---

### User Story 2 - Il capo unità iscrive un ragazzo della sua unità (Priority: P1)

Un capo con servizio in un'unità iscrive un lupetto/coccinella o un esploratore/guida della propria unità, indicandone il codice socio. Il capo è il presentatore dell'iscrizione e la può seguire.

**Why this priority**: per L/C ed E/G è l'unico modo di iscriversi (BR-02); senza, metà dei soci non può partecipare.

**Independent Test**: con un capo e un ragazzo E/G della stessa unità, iscrivere il ragazzo a un evento E/G e verificare che l'iscrizione abbia il ragazzo come partecipante e il capo come presentatore.

**Acceptance Scenarios**:

1. **Given** un capo dell'unità X e un esploratore della stessa unità, **When** il capo lo iscrive a un evento aperto adatto, **Then** l'iscrizione è REGISTRATA con il ragazzo come partecipante, il capo come presentatore e la validazione del presentatore già soddisfatta.
2. **Given** un capo dell'unità X e un ragazzo di un'altra unità, **When** il capo prova a iscriverlo, **Then** l'iscrizione è rifiutata per mancanza di permesso.
3. **Given** un capo e un rover della sua unità, **When** il capo prova a iscriverlo e l'evento ammette solo l'iscrizione autonoma, **Then** l'iscrizione è rifiutata per modalità non consentita.
4. **Given** un ragazzo che non soddisfa l'età minima dell'evento alla data di partenza, **When** il capo prova a iscriverlo, **Then** l'iscrizione è rifiutata con il motivo.

---

### User Story 3 - Seguire lo stato dell'iscrizione fino al completamento (Priority: P2)

Il socio (o il suo presentatore) apre la pagina di stato dell'iscrizione e vede stato, posizione, adempimenti con il loro stato, scadenza e azioni possibili. I capi indicati confermano o negano dal sito le validazioni e le autorizzazioni; la segreteria segna come soddisfatti gli adempimenti che, fino alle fasi successive, il sistema non sa verificare da solo (pagamento, documenti, scheda). Quando tutti gli adempimenti sono soddisfatti l'iscrizione diventa COMPLETA; se alla scadenza non lo è, diventa SCADUTA da sola.

**Why this priority**: senza completamento le iscrizioni restano a metà; ma la registrazione (US1, US2) ha già valore da sola.

**Independent Test**: registrare un'iscrizione con due adempimenti, soddisfarli uno alla volta e verificare il passaggio a COMPLETA; registrarne un'altra, spostare l'ora oltre i 9 giorni e verificare SCADUTA.

**Acceptance Scenarios**:

1. **Given** un'iscrizione REGISTRATA con un'autorizzazione del capo gruppo da fare, **When** il capo gruppo del partecipante la autorizza, **Then** l'adempimento è soddisfatto, con chi e quando.
2. **Given** la stessa autorizzazione, **When** il capo gruppo la nega con un motivo, **Then** l'iscrizione diventa RIFIUTATA con il motivo e, se era nei posti, il primo in attesa è promosso.
3. **Given** un'iscrizione con un solo adempimento mancante, **When** viene soddisfatto, **Then** l'iscrizione diventa COMPLETA nello stesso momento.
4. **Given** un'iscrizione REGISTRATA da più di 9 giorni con adempimenti mancanti, **When** passa il controllo periodico, **Then** l'iscrizione diventa SCADUTA e, se era nei posti, il primo in attesa è promosso.
5. **Given** un utente che non è il partecipante, il presentatore, un capo con autorizzazione da dare o la segreteria dell'evento, **When** apre la pagina di stato, **Then** l'iscrizione risulta inesistente.

---

### User Story 4 - Ritirarsi e liberare il posto (Priority: P2)

Il socio o il suo presentatore ritira l'iscrizione prima della partenza. Il sistema dice se spetta il rimborso secondo BR-10 e, se l'iscrizione era nei posti, promuove il primo in lista d'attesa. Anche quando la segreteria aumenta i posti i primi in attesa sono promossi.

**Why this priority**: è la regola che rende equa la lista d'attesa (BR-08) ed è la condizione per i rimborsi della fase 5.

**Independent Test**: con un evento pieno e un iscritto in attesa, ritirare un iscritto nei posti e verificare RITIRATA, diritto al rimborso e promozione dell'attesa; aumentare i posti e verificare altre promozioni.

**Acceptance Scenarios**:

1. **Given** un'iscrizione nei posti e un ritiro 10 giorni prima della partenza, **When** il socio si ritira, **Then** l'iscrizione è RITIRATA con diritto al rimborso e il primo in attesa diventa nei posti.
2. **Given** un ritiro 3 giorni prima della partenza, **When** il socio si ritira, **Then** l'iscrizione è RITIRATA senza diritto al rimborso.
3. **Given** un evento già partito, **When** il socio prova a ritirarsi, **Then** il ritiro è rifiutato come conflitto.
4. **Given** un evento con 30 posti, 30 nei posti e 3 in attesa, **When** la segreteria porta i posti a 32, **Then** i primi 2 in attesa diventano nei posti, in ordine di arrivo.
5. **Given** iscrizioni ancora in attesa alla data di partenza, **When** passa il controllo periodico, **Then** diventano ANNULLATE con motivo "posti esauriti".

---

### User Story 5 - Elenchi e annullamento dell'evento (Priority: P3)

Il socio vede l'elenco delle proprie iscrizioni; il capo quello delle iscrizioni che ha presentato; la segreteria l'elenco degli iscritti di un evento della propria regione, filtrabile per stato e posizione. Se la segreteria annulla un evento, tutte le iscrizioni attive diventano ANNULLATE, con diritto al rimborso per chi era nei posti. Il catalogo mostra il numero reale di iscritti e l'indicatore di disponibilità.

**Why this priority**: utile e atteso dal frontend, ma l'iscrizione funziona anche senza.

**Independent Test**: con iscrizioni di più soci su più eventi, controllare i tre elenchi; annullare un evento e verificare le iscrizioni ANNULLATE; controllare iscritti e indicatore nel dettaglio pubblico.

**Acceptance Scenarios**:

1. **Given** un socio con iscrizioni a 3 eventi, **When** apre "le mie iscrizioni", **Then** vede le 3 iscrizioni con evento, stato e posizione.
2. **Given** la segreteria del Piemonte, **When** apre l'elenco iscritti di un evento del Piemonte, **Then** vede tutti gli iscritti con stato, posizione e adempimenti; per un evento della Liguria l'accesso è negato.
3. **Given** un evento con 5 iscrizioni attive, **When** la segreteria lo annulla, **Then** le 5 iscrizioni diventano ANNULLATE con motivo "evento annullato" e diritto al rimborso per quelle nei posti (quota maggiore di 0).
4. **Given** un evento con 30 posti e 28 iscrizioni attive, **When** un visitatore ne apre il dettaglio, **Then** vede 28 iscritti e l'indicatore LIMITATE.

---

### Edge Cases

- Due soci si iscrivono nello stesso istante all'ultimo posto: uno solo è nei posti, l'altro in attesa; nessun ordine di arrivo è doppio o saltato.
- Molte iscrizioni allo stesso evento alle 09:00 in punto (picco di apertura): l'ordine di arrivo resta unico e crescente.
- La segreteria riduce i posti sotto il numero di iscritti già nei posti: rifiutato come conflitto, nessuno perde il posto.
- Un'iscrizione in attesa che si ritira o scade: nessuna promozione, la lista scala.
- Un'iscrizione RITIRATA, SCADUTA, RIFIUTATA o ANNULLATA non conta come "iscrizione attiva": il socio si può iscrivere di nuovo allo stesso evento, con un nuovo ordine di arrivo.
- Evento con quota 0: nessun adempimento di pagamento.
- Evento senza adempimenti: l'iscrizione nasce già COMPLETA.
- Ritiro esattamente 7 giorni prima della partenza: rimborso spettante (BR-10, data di partenza esclusa).
- Il partecipante compie gli anni tra l'iscrizione e la partenza: conta l'età alla data di partenza.
- La segreteria modifica le date dell'evento dopo le iscrizioni: gli iscritti restano, i requisiti non sono ricontrollati.
- Database non raggiungibile: servizio non disponibile con l'envelope, come nelle fasi precedenti.

## Requirements *(mandatory)*

### Registrazione

- **FR-001**: Un socio con l'entitlement `agesci:entitlement:iscrizione:self` MUST potersi iscrivere a un evento con iscrizioni aperte se è rover/scolta o capo (BR-02) e l'evento ammette l'iscrizione autonoma (FR-023 di progetto).
- **FR-002**: Un capo con servizio in un'unità MUST poter iscrivere un lupetto/coccinella o un esploratore/guida della stessa unità, indicandone il codice socio, se l'evento ammette l'iscrizione tramite capo; il capo diventa presentatore dell'iscrizione (BR-02).
- **FR-003**: Alla registrazione il sistema MUST verificare: iscrizioni dell'evento aperte; partecipante censito e in regola per l'anno in corso (BR-01, FR-011 di progetto); modalità di iscrizione consentita; requisiti dell'evento (BR-06: età minima e massima alla data di partenza, branche ammesse, tipo socio, una sola iscrizione attiva a eventi dello stesso tipo, massimo di iscritti dello stesso gruppo); nessuna iscrizione attiva del partecipante allo stesso evento. Ogni rifiuto MUST indicare il motivo (FR-024 di progetto).
- **FR-004**: La regione, zona o gruppo del partecipante MUST NOT limitare l'iscrizione (BR-15).
- **FR-005**: Il sistema MUST assegnare a ogni iscrizione un ordine di arrivo unico e crescente per evento, anche con molte richieste nello stesso istante; le prime iscrizioni attive per ordine di arrivo, fino al numero di posti, sono "nei posti", le altre "in attesa" (BR-07, FR-025 di progetto).
- **FR-006**: Ogni iscrizione MUST avere un numero leggibile univoco.

### Adempimenti e completamento

- **FR-007**: All'iscrizione il sistema MUST creare gli adempimenti richiesti dall'evento (FR-026 di progetto); la validazione del presentatore è già soddisfatta quando è il presentatore stesso a registrare l'iscrizione; il pagamento non è richiesto se la quota è 0.
- **FR-008**: Ogni iscrizione MUST avere una scadenza di completamento a 9 giorni di calendario dalla registrazione (BR-04, FR-027 di progetto), visibile nella pagina di stato.
- **FR-009**: Il capo unità e il capo gruppo del partecipante MUST poter autorizzare o negare, con motivo obbligatorio per il diniego, gli adempimenti di autorizzazione che li riguardano (FR-029 di progetto). Il diniego rende l'iscrizione RIFIUTATA.
- **FR-010**: La segreteria che gestisce l'evento MUST poter segnare come soddisfatto, con una nota, qualsiasi adempimento dell'iscrizione: fino alle fasi 5 e 7 è l'unico modo per pagamento, documenti e scheda.
- **FR-011**: Quando l'ultimo adempimento è soddisfatto l'iscrizione MUST diventare COMPLETA nello stesso momento (FR-030 di progetto); un'iscrizione senza adempimenti nasce COMPLETA.
- **FR-012**: Un'iscrizione REGISTRATA non completata entro la scadenza MUST diventare SCADUTA al più tardi 15 minuti dopo la scadenza (FR-031 di progetto).

### Lista d'attesa

- **FR-013**: Quando un'iscrizione nei posti esce (ritiro, scadenza, rifiuto, annullamento) il sistema MUST promuovere la prima iscrizione attiva in attesa per ordine di arrivo (BR-08, FR-036 di progetto).
- **FR-014**: Quando la segreteria aumenta i posti di un evento, il sistema MUST promuovere tanti iscritti in attesa quanti sono i posti in più (FR-037 di progetto); una riduzione dei posti sotto il numero di iscrizioni nei posti MUST essere rifiutata come conflitto.
- **FR-015**: La pagina di stato MUST mostrare la posizione in lista d'attesa (1 = il prossimo a essere promosso) (FR-035 di progetto).
- **FR-016**: Le iscrizioni ancora in attesa alla data di partenza MUST diventare ANNULLATE con motivo "posti esauriti".
- **FR-017**: Un'iscrizione in attesa MUST NOT avere l'adempimento di pagamento: deve completare entro la scadenza gli altri adempimenti. Alla promozione il sistema MUST aggiungere il pagamento (se la quota è maggiore di 0), riportare l'iscrizione a REGISTRATA se era COMPLETA e fissare una nuova scadenza a 9 giorni dalla promozione (FR-038 di progetto; opzione consigliata, in attesa della conferma di Mahima del 14/10).

### Ritiro e annullamento

- **FR-018**: Il partecipante o il presentatore MUST poter ritirare un'iscrizione REGISTRATA o COMPLETA prima dell'inizio dell'evento (BR-09, FR-032 di progetto).
- **FR-019**: Al ritiro il sistema MUST calcolare e salvare il diritto al rimborso: spetta se il ritiro avviene almeno 7 giorni prima della data di partenza, data di partenza esclusa (BR-10, Q12), e solo a chi era nei posti con quota maggiore di 0: chi è in attesa non ha pagato (FR-017). Il rimborso effettivo è della fase 5.
- **FR-020**: L'annullamento di un evento MUST rendere ANNULLATE tutte le sue iscrizioni attive con motivo "evento annullato" e diritto al rimborso per quelle nei posti con quota maggiore di 0 (FR-021 di progetto).

### Consultazione

- **FR-021**: La pagina di stato dell'iscrizione MUST mostrare evento, partecipante, presentatore, stato, posizione, ordine di arrivo, adempimenti con il loro stato, scadenza, diritto al rimborso e azioni disponibili (FR-033 di progetto). La vedono solo il partecipante, il presentatore, i capi che hanno un'autorizzazione da dare e chi gestisce l'evento; per gli altri l'iscrizione risulta inesistente.
- **FR-022**: Il socio MUST poter vedere l'elenco delle proprie iscrizioni e il capo quello delle iscrizioni che ha presentato (FR-034 di progetto).
- **FR-023**: Chi gestisce un evento (stessa regola territoriale della fase 2) MUST poter vedere l'elenco paginato dei suoi iscritti, filtrabile per stato e posizione.
- **FR-024**: Il catalogo pubblico MUST mostrare per ogni evento il numero di iscrizioni attive e l'indicatore di disponibilità calcolato su di esso (BR-14).

### Errori

- **FR-025**: Gli errori MUST usare l'envelope e i codici delle fasi precedenti con status HTTP significativo: 403 per entitlement o relazione con la persona mancante, 404 per iscrizione inesistente o non visibile, 409 per stato dell'evento o dell'iscrizione non compatibile o iscrizione già attiva, 422 per requisiti non soddisfatti con l'elenco dei motivi.

### Key Entities *(include if feature involves data)*

- **Iscrizione**: lega un partecipante (socio) a un evento; numero leggibile, presentatore facoltativo, stato (REGISTRATA, COMPLETA, SCADUTA, RITIRATA, RIFIUTATA, ANNULLATA), posizione (nei posti / in attesa), ordine di arrivo, data di registrazione, scadenza, data di completamento, motivo di chiusura, diritto al rimborso.
- **Adempimento dell'iscrizione**: parte dell'iscrizione; tipo (validazione del presentatore, autorizzazione capo unità, autorizzazione capo gruppo, pagamento, documento, scheda), stato (da fare, soddisfatto, rifiutato, non richiesto), chi e quando, nota.
- **Capienza dell'evento**: contatore dell'ordine di arrivo e numero di iscrizioni nei posti e attive per evento, aggiornato in modo atomico.
- **Socio (dati usati)**: dal censimento: codice socio, gruppo, unità, branca, tipo socio (ragazzo/capo), stato e anno del censimento, capo gruppo sì/no; dall'utente: data di nascita.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un socio idoneo completa l'iscrizione a un evento in meno di 1 minuto di lavoro sul frontend.
- **SC-002**: Con 200 richieste di iscrizione allo stesso evento nello stesso secondo, il 100% riceve un ordine di arrivo diverso, senza buchi, e il numero di iscrizioni nei posti è esattamente uguale ai posti.
- **SC-003**: Il 100% delle uscite dai posti (ritiro, scadenza, rifiuto, annullamento) promuove il primo in attesa, quando c'è.
- **SC-004**: Il 100% delle iscrizioni non complete è SCADUTO entro 15 minuti dalla scadenza dei 9 giorni.
- **SC-005**: Il diritto al rimborso è corretto nel 100% dei casi di prova, compreso il ritiro esattamente 7 giorni prima della partenza.
- **SC-006**: Nessuna iscrizione è rifiutata per la regione del partecipante (BR-15) nei casi di prova tra regioni diverse.

## Assumptions

- Data di nascita: si aggiunge all'utente (spec di progetto §2.3.3) e arriva dal censimento; nel mock è nel file `mocked-identity.yml`. Serve per l'età alla partenza.
- Capo unità e capo gruppo si riconoscono dal censimento: un capo con la stessa unità del ragazzo è il suo capo unità; il capo gruppo ha un'indicazione "capo gruppo" nel censimento ed è dello stesso gruppo. Le strutture complete (fase "strutture") non servono.
- Il capo presentatore che registra l'iscrizione la valida di fatto; il link firmato via e-mail per i presentatori indicati da altri arriva con le notifiche (fase 4). In questa fase un R/S che si iscrive da solo non indica un presentatore.
- Socio "in regola": censimento con stato in regola e anno uguale all'anno in corso.
- I casi particolari (iscrizione assistita da parte della segreteria, proroghe, BR-01) sono fuori da questa feature.
- Il controllo periodico per scadenze e lista d'attesa alla partenza gira ogni 5 minuti; con una sola istanza dell'applicazione non serve un lock distribuito.
- Gli eventi di dominio verso Kafka e le e-mail di conferma, promozione e scadenza arrivano con la fase 4; questa feature non invia notifiche.
- Lo staff dell'evento (elenchi per i capi campo) arriva con l'area staff; qui l'elenco iscritti è della segreteria e dell'amministrazione.
