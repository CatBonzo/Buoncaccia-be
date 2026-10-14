# Feature Specification: Catalogo eventi

**Feature Branch**: `002-catalogo-eventi`

**Created**: 2026-10-13

**Status**: Draft

**Input**: User description: "Fase 2 del Planner v2: catalogo eventi. Tipi evento (FR-013, US-17), creazione evento in bozza (FR-014), configurazione di requisiti, adempimenti e modalità di iscrizione (FR-015), pubblicazione con validazione (FR-016), apertura e chiusura automatica delle iscrizioni alle 09:00 Europe/Rome e chiusura anticipata (FR-017, BR-03), ricerca pubblica paginata con filtri e ordinamento (FR-018, US-01), dettaglio pubblico con indicatore di disponibilità (FR-019, BR-14, US-02), modifica limitata dopo la pubblicazione (FR-022), annullamento motivato (FR-021), stati dell'evento (§2.3.6). Autorizzazioni con gli entitlement del token (AUTH-1); decisione Q11 del 13/10: la segreteria regionale crea e modifica solo gli eventi della propria regione; i tipi evento li gestisce l'amministrazione. Fuori: iscrizioni, staff, strutture e anagrafica completa (regioni come lista fissa), notifiche. Fonte: Note Tecniche Backend v2.0."

## Clarifications

### Session 2026-10-13

- Q11 (spec di progetto): la segreteria regionale può gestire eventi di qualunque regione? → A: no, solo quelli della propria regione; decisa da Mahima (vedi FR-010).
- Da quale informazione del token si ricava la regione della segreteria? → A: dall'appartenenza `isMemberOf`, con la sigla della regione al posto di "regione": `agesci:PIE:segreteria`; scelta di Mahima (vedi FR-010). La residenza di chi è censito non conta.
- Default proposti e confermati da Mahima: eventi nazionali gestiti dall'amministrazione; regioni come lista fissa e zona come testo; campi bloccati dopo la pubblicazione come in FR-016.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Creare e pubblicare un evento della propria regione (Priority: P1)

La segreteria regionale prepara un evento (per esempio un campo di formazione) in bozza: sceglie il tipo evento, inserisce titolo, descrizione, date, luogo, posti, quota, finestra delle iscrizioni, requisiti, adempimenti e modalità di iscrizione. Quando è completo lo pubblica; da quel momento è visibile a tutti.

**Why this priority**: senza eventi pubblicati non c'è catalogo né, nelle fasi dopo, iscrizioni. È il cuore della feature.

**Independent Test**: con il token della segreteria di una regione, creare un evento di quella regione, pubblicarlo e verificare che compaia nella ricerca pubblica.

**Acceptance Scenarios**:

1. **Given** la segreteria della regione Piemonte, **When** crea un evento con regione Piemonte e tutti i dati obbligatori, **Then** l'evento è salvato in stato BOZZA e non compare nella ricerca pubblica.
2. **Given** un evento in BOZZA completo e coerente, **When** la segreteria lo pubblica, **Then** passa a PUBBLICATO e compare nella ricerca pubblica.
3. **Given** un evento in BOZZA con la data di rientro precedente alla partenza (o un altro invariante violato), **When** la segreteria prova a pubblicarlo, **Then** la pubblicazione è rifiutata come input non valido, con l'elenco dei campi da correggere, e l'evento resta in BOZZA.
4. **Given** la segreteria della regione Piemonte, **When** prova a creare o modificare un evento della regione Liguria, **Then** l'operazione è rifiutata per mancanza di permesso.
5. **Given** un utente senza l'entitlement di gestione eventi (es. un socio), **When** prova a creare un evento, **Then** l'operazione è rifiutata per mancanza di permesso.

---

### User Story 2 - Cercare eventi e vederne il dettaglio senza login (Priority: P1)

Un visitatore, anche senza accesso, cerca gli eventi filtrando per regione, categoria, tipo, stato delle iscrizioni e intervallo di date, li ordina per tipo, data di partenza o titolo, e apre il dettaglio di un evento per sapere date, luogo, quota, posti, requisiti, adempimenti, modalità di iscrizione, contatti e disponibilità.

**Why this priority**: è ciò che oggi la gente usa di più del sito (US-01, US-02) e serve al frontend fin da subito.

**Independent Test**: con alcuni eventi pubblicati in regioni e date diverse, eseguire ricerche con filtri e ordinamenti diversi senza token e controllare risultati, ordine e paginazione; aprire un dettaglio.

**Acceptance Scenarios**:

1. **Given** eventi pubblicati in Piemonte e Liguria, **When** un visitatore cerca con filtro regione Piemonte, **Then** riceve solo gli eventi del Piemonte.
2. **Given** 25 eventi pubblicati, **When** un visitatore chiede la prima pagina da 10 ordinata per data di partenza, **Then** riceve 10 eventi in ordine di partenza e il totale di 25.
3. **Given** un evento in BOZZA, **When** un visitatore lo cerca o ne chiede il dettaglio, **Then** l'evento non compare e il dettaglio risulta inesistente.
4. **Given** un evento pubblicato con 30 posti, **When** un visitatore ne apre il dettaglio, **Then** vede posti, iscritti e l'indicatore di disponibilità coerente con lo stato e con BR-14.

---

### User Story 3 - Apertura e chiusura delle iscrizioni (Priority: P2)

Le iscrizioni di un evento pubblicato si aprono da sole alle 09:00 (ora italiana) del giorno di apertura e si chiudono da sole alla fine del giorno di chiusura. La segreteria può chiuderle prima. Anche gli stati successivi (in corso, concluso) cambiano da soli in base alle date.

**Why this priority**: è la regola che decide quando ci si può iscrivere (fase 3); serve pronta prima delle iscrizioni ma non blocca la consultazione del catalogo.

**Independent Test**: con un orologio controllato, portare il tempo oltre le 09:00 del giorno di apertura e oltre la chiusura e verificare gli stati; chiudere a mano un evento con iscrizioni aperte.

**Acceptance Scenarios**:

1. **Given** un evento PUBBLICATO con apertura il 1° marzo, **When** sono le 08:59 del 1° marzo (ora italiana), **Then** l'evento è ancora PUBBLICATO e l'indicatore è DA_APRIRE.
2. **Given** lo stesso evento, **When** sono le 09:00 del 1° marzo, **Then** l'evento è ISCRIZIONI_APERTE.
3. **Given** un evento ISCRIZIONI_APERTE con chiusura il 10 marzo, **When** sono le 00:00 dell'11 marzo, **Then** l'evento è ISCRIZIONI_CHIUSE.
4. **Given** un evento ISCRIZIONI_APERTE, **When** la segreteria della sua regione lo chiude in anticipo, **Then** l'evento è ISCRIZIONI_CHIUSE subito.
5. **Given** un evento ISCRIZIONI_CHIUSE, **When** arriva la data di partenza e poi la data di rientro è superata, **Then** l'evento passa a IN_CORSO e poi a CONCLUSO.

---

### User Story 4 - Gestire i tipi evento (Priority: P2)

L'amministrazione mantiene il catalogo dei tipi evento (per esempio CFM_EG, ROSS, CFA): codice, nome, categoria, regole e adempimenti predefiniti, e se il tipo è a candidatura. Quando la segreteria crea un evento di un tipo, l'evento parte con le regole e gli adempimenti predefiniti del tipo, che può poi modificare.

**Why this priority**: gli eventi hanno bisogno di un tipo, ma un catalogo iniziale può bastare per partire; la gestione completa può seguire la creazione degli eventi.

**Independent Test**: con il token dell'amministrazione creare, modificare e disattivare un tipo evento; con il token della segreteria verificare che non possa farlo; creare un evento di quel tipo e controllare i valori predefiniti.

**Acceptance Scenarios**:

1. **Given** l'amministrazione, **When** crea un tipo evento con un codice nuovo, **Then** il tipo è disponibile per creare eventi.
2. **Given** un tipo evento con codice CFM_EG già esistente, **When** l'amministrazione ne crea un altro con lo stesso codice, **Then** la richiesta è rifiutata come conflitto.
3. **Given** la segreteria regionale, **When** prova a creare o modificare un tipo evento, **Then** l'operazione è rifiutata per mancanza di permesso.
4. **Given** un tipo evento con requisiti e adempimenti predefiniti, **When** la segreteria crea un evento di quel tipo senza indicarli, **Then** l'evento ha i requisiti e gli adempimenti del tipo.
5. **Given** un tipo evento usato da almeno un evento, **When** l'amministrazione lo disattiva, **Then** non è più selezionabile per nuovi eventi ma gli eventi esistenti restano invariati.

---

### User Story 5 - Modificare o annullare un evento pubblicato (Priority: P3)

Dopo la pubblicazione la segreteria può correggere solo alcuni dati dell'evento; se serve, lo annulla indicando il motivo.

**Why this priority**: è necessario per la gestione reale, ma senza iscrizioni (fase 3) le conseguenze di modifica e annullamento sono limitate.

**Independent Test**: su un evento pubblicato provare a cambiare un campo consentito e uno non consentito; annullare l'evento con e senza motivazione.

**Acceptance Scenarios**:

1. **Given** un evento PUBBLICATO, **When** la segreteria modifica la descrizione o i contatti, **Then** la modifica è salvata.
2. **Given** un evento PUBBLICATO, **When** la segreteria prova a cambiare tipo evento, livello, regione o codice, **Then** la modifica è rifiutata come conflitto di stato.
3. **Given** un evento in uno stato da BOZZA a IN_CORSO, **When** la segreteria lo annulla indicando un motivo, **Then** l'evento è ANNULLATO, il motivo è registrato e l'evento resta visibile nel dettaglio come annullato.
4. **Given** un evento, **When** la segreteria prova ad annullarlo senza motivo, **Then** la richiesta è rifiutata come input non valido.
5. **Given** un evento CONCLUSO o già ANNULLATO, **When** la segreteria prova ad annullarlo o modificarlo, **Then** la richiesta è rifiutata come conflitto di stato.

---

### Edge Cases

- Ricerca senza risultati: risposta di successo con elenco vuoto e totale 0, non un errore.
- Pagina oltre l'ultima: elenco vuoto con il totale reale.
- Parametri di ricerca non validi (data non valida, pagina negativa, ordinamento sconosciuto): input non valido con l'indicazione del parametro.
- Apertura iscrizioni nel passato al momento della pubblicazione: alla pubblicazione l'evento passa direttamente allo stato che corrisponde all'ora corrente (es. ISCRIZIONI_APERTE).
- Chiusura iscrizioni successiva alla data di partenza: invariante violato, la pubblicazione è rifiutata.
- Cambio d'ora legale/solare: l'apertura resta alle 09:00 ora italiana del giorno indicato.
- Due segreterie che modificano lo stesso evento nello stesso momento: la seconda modifica basata su dati superati è rifiutata come conflitto, senza perdere silenziosamente la prima.
- Evento con quota 0: valido (es. eventi a candidatura, BR-12); nessun adempimento di pagamento richiesto.
- Tipo evento disattivato dopo la creazione di una bozza: la bozza si può ancora pubblicare.
- Database non raggiungibile: servizio non disponibile, con l'envelope e senza dettagli interni (come nella fase 1).

## Requirements *(mandatory)*

### Functional Requirements

**Tipi evento**

- **FR-001**: L'amministrazione MUST poter creare, modificare, consultare e disattivare i tipi evento con: codice univoco, nome, categoria, requisiti predefiniti, adempimenti predefiniti, indicazione "a candidatura", stato attivo/disattivo (FR-013 di progetto, US-17).
- **FR-002**: Le operazioni di gestione dei tipi evento MUST richiedere l'entitlement `agesci:entitlement:tipoevento:gestione`; la consultazione dei tipi attivi MUST essere pubblica (serve ai filtri del catalogo).
- **FR-003**: Un tipo evento disattivato MUST NOT essere usabile per nuovi eventi; gli eventi esistenti di quel tipo MUST restare invariati. I tipi evento MUST NOT essere cancellati se usati da almeno un evento.

**Eventi: creazione, configurazione, pubblicazione**

- **FR-004**: Chi ha l'entitlement `agesci:entitlement:evento:gestione` MUST poter creare un evento in stato BOZZA con: tipo evento, titolo, descrizione, livello (nazionale, regionale, zona), regione, zona (se il livello è zona), località, provincia, data di partenza, data di rientro, data di apertura e di chiusura delle iscrizioni, posti, quota, modalità di iscrizione, requisiti, adempimenti, contatti metodologico e amministrativo (FR-014 di progetto). Il sistema MUST assegnare all'evento un identificativo e un codice leggibile univoci.
- **FR-005**: Se requisiti o adempimenti non sono indicati, l'evento MUST partire con quelli predefiniti del tipo evento; la segreteria MUST poterli cambiare finché l'evento è in BOZZA (FR-015 di progetto).
- **FR-006**: I requisiti configurabili MUST comprendere almeno quelli di BR-06: età minima e massima alla data di partenza, branche ammesse, tipo socio (ragazzo/capo), una sola iscrizione attiva a eventi dello stesso tipo, numero massimo di iscritti dello stesso gruppo. Gli adempimenti configurabili MUST essere quelli di BR-05: validazione del capo presentatore, autorizzazione capo unità, autorizzazione capo gruppo, pagamento, documenti richiesti, scheda elettronica. La modalità di iscrizione MUST essere autonoma, tramite presentatore o entrambe. In questa feature requisiti e adempimenti sono solo descritti e mostrati; la loro verifica arriva con le iscrizioni.
- **FR-007**: La pubblicazione MUST verificare tutti gli invarianti dell'evento e, se uno fallisce, MUST rifiutarla elencando tutti i campi da correggere (FR-016 di progetto). Invarianti minimi: campi obbligatori presenti; tipo evento esistente (può essere stato disattivato dopo la creazione della bozza, vedi i casi limite); partenza ≤ rientro; apertura iscrizioni < chiusura iscrizioni ≤ partenza; posti > 0; quota ≥ 0; età minima ≤ età massima se entrambe indicate; zona indicata se e solo se il livello è zona; regione indicata salvo per il livello nazionale.
- **FR-008**: In BOZZA l'evento MUST essere modificabile in tutti i campi e salvabile anche incompleto; la verifica completa avviene alla pubblicazione.
- **FR-009**: Gli eventi in BOZZA MUST essere visibili solo a chi può gestirli (FR-010); il sistema MUST offrire a chi gestisce gli eventi l'elenco dei propri eventi in tutti gli stati, compresa la BOZZA.

**Autorizzazioni territoriali (Q11)**

- **FR-010**: La segreteria regionale MUST poter creare, modificare, pubblicare, chiudere e annullare solo eventi della propria regione (livello regionale o zona). L'amministrazione MUST poter gestire eventi di qualsiasi regione e di livello nazionale. La regione della segreteria MUST essere letta dall'appartenenza `isMemberOf` del token, nella forma `agesci:<SIGLA_REGIONE>:segreteria` (es. `agesci:PIE:segreteria`), dove la sigla è quella della lista fissa delle regioni (FR-022). Una segreteria senza un'appartenenza di questa forma, o con una sigla sconosciuta, MUST NOT poter gestire alcun evento.
- **FR-011**: Il vincolo di FR-010 MUST riguardare solo la gestione degli eventi: la consultazione del catalogo e, nelle fasi successive, le iscrizioni MUST restare senza vincolo territoriale (BR-15).

**Stati e tempi**

- **FR-012**: Gli stati dell'evento MUST essere BOZZA, PUBBLICATO, ISCRIZIONI_APERTE, ISCRIZIONI_CHIUSE, IN_CORSO, CONCLUSO, ANNULLATO, con le sole transizioni di §2.3.6 della spec di progetto; ogni altra transizione MUST essere rifiutata come conflitto di stato.
- **FR-013**: Le iscrizioni MUST aprirsi alle 09:00 (Europe/Rome) del giorno di apertura e chiudersi alle 23:59:59 (Europe/Rome) del giorno di chiusura (BR-03). Il passaggio a IN_CORSO MUST avvenire all'inizio del giorno di partenza e quello a CONCLUSO dopo la fine del giorno di rientro.
- **FR-014**: I cambi di stato dovuti al tempo MUST valere al più tardi entro un minuto dall'orario previsto, e lo stato mostrato nel catalogo e nel dettaglio MUST essere sempre coerente con l'ora corrente, anche se l'aggiornamento automatico non è ancora passato.
- **FR-015**: La segreteria MUST poter chiudere in anticipo le iscrizioni di un evento ISCRIZIONI_APERTE della propria regione (FR-017 di progetto).

**Modifica e annullamento dopo la pubblicazione**

- **FR-016**: Dopo la pubblicazione MUST essere modificabili solo: titolo, descrizione, località, provincia, contatti, posti, quota, date di partenza e rientro, data di chiusura delle iscrizioni (se non ancora passata). MUST NOT essere modificabili: tipo evento, livello, regione, zona, codice, modalità di iscrizione, requisiti e adempimenti. Ogni modifica MUST rispettare gli invarianti di FR-007 (FR-022 di progetto). La notifica agli iscritti della modifica di date, quota o posti è fuori da questa feature.
- **FR-017**: La segreteria MUST poter annullare un evento in uno stato da BOZZA a IN_CORSO indicando un motivo obbligatorio; l'evento annullato MUST restare consultabile nel dettaglio con stato e motivo, e MUST NOT comparire nella ricerca salvo filtro esplicito per stato (FR-021 di progetto, senza iscrizioni da annullare in questa feature).
- **FR-018**: Due modifiche concorrenti allo stesso evento MUST NOT perdersi in silenzio: una modifica basata su una versione superata dell'evento MUST essere rifiutata come conflitto.

**Catalogo pubblico**

- **FR-019**: Il sistema MUST offrire senza autenticazione la ricerca degli eventi pubblicati (stati da PUBBLICATO a CONCLUSO) con filtri per regione, categoria, tipo evento, stato delle iscrizioni (indicatore di FR-021), intervallo di date di partenza e testo nel titolo; ordinamento per tipo, data di partenza o titolo, crescente o decrescente; risultati paginati con numero di pagina, dimensione (massimo 100) e totale (FR-018 di progetto).
- **FR-020**: Il sistema MUST offrire senza autenticazione il dettaglio di un evento pubblicato o annullato: tutti i dati di FR-004 tranne quelli interni, posti, iscritti e indicatore di disponibilità (FR-019 di progetto). Il dettaglio di un evento in BOZZA MUST risultare inesistente per chi non può gestirlo.
- **FR-021**: L'indicatore di disponibilità MUST essere: DA_APRIRE (prima dell'apertura), APERTE (iscritti < 90% dei posti), LIMITATE (dal 90% al 120%), SOVRANNUMERO_PESANTE (oltre il 120%), CHIUSE (iscrizioni chiuse, evento in corso, concluso o annullato), secondo BR-14. In questa feature gli iscritti sono sempre 0, finché le iscrizioni non arrivano nella fase 3.
- **FR-022**: Le regioni MUST essere una lista fissa delle regioni AGESCI con codice e nome, consultabile pubblicamente; le zone in questa feature sono un testo libero legato alla regione. La gestione completa delle strutture (FR-009, FR-010 di progetto) è fuori da questa feature.

**Errori**

- **FR-023**: Gli errori di questa feature MUST usare l'envelope e i codici della fase 1, con status HTTP significativo: 401 senza token valido sulle operazioni protette, 403 per entitlement o regione non consentiti, 404 per evento o tipo inesistente (o in BOZZA per chi non può vederlo), 409 per transizione di stato non consentita, codice duplicato o versione superata, 422 per input non valido con l'elenco dei campi.

### Key Entities *(include if feature involves data)*

- **Tipo evento**: codice univoco (es. CFM_EG, ROSS, CFA), nome, categoria, requisiti predefiniti, adempimenti predefiniti, a candidatura sì/no, attivo sì/no.
- **Evento**: identificativo, codice leggibile, tipo evento, titolo, descrizione, livello (nazionale, regionale, zona), regione, zona, località, provincia, partenza, rientro, apertura e chiusura delle iscrizioni, posti, quota, modalità di iscrizione, requisiti, adempimenti, contatti metodologico e amministrativo, stato, motivo di annullamento, versione, chi l'ha creato e quando, ultima modifica.
- **Requisiti di partecipazione** (parte dell'evento): età minima e massima, branche ammesse, tipo socio, una sola iscrizione per tipo, massimo per gruppo.
- **Adempimento richiesto** (parte dell'evento): tipo (validazione presentatore, autorizzazione capo unità, autorizzazione capo gruppo, pagamento, documento, scheda) ed eventuale descrizione (es. "foto JPG ≤ 1 MB").
- **Regione**: codice e nome, da lista fissa.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Una segreteria crea e pubblica un evento completo in meno di 5 minuti di lavoro sul frontend, con al massimo una correzione dopo l'elenco degli errori di validazione.
- **SC-002**: Il 100% dei tentativi di una segreteria di gestire eventi di un'altra regione è rifiutato, e il 100% di quelli sulla propria regione è accettato se i dati sono validi.
- **SC-003**: Il 100% degli eventi in BOZZA è invisibile a visitatori e soci, sia nella ricerca sia nel dettaglio.
- **SC-004**: Con 1.000 eventi pubblicati, una ricerca con filtri restituisce la prima pagina in meno di 1 secondo.
- **SC-005**: Lo stato mostrato di ogni evento è corretto rispetto all'ora italiana nel 100% dei casi di prova, compresi i giorni di cambio dell'ora legale; le iscrizioni non risultano mai aperte prima delle 09:00 del giorno di apertura.
- **SC-006**: Il 100% delle risposte, comprese quelle di errore, ha i quattro campi dell'envelope.

## Assumptions

- Ruoli dell'MVP come nella fase 1: amministrazione (tutti gli entitlement, nessun vincolo di regione), segreteria regionale (`evento:gestione`, limitata alla propria regione), user (solo consultazione). Gli eventi nazionali li gestisce l'amministrazione, perché la segreteria nazionale non è nell'MVP.
- La segreteria regionale gestisce gli eventi di livello regionale e di zona della propria regione.
- Le soglie di BR-14 (90% e 120%) sono quelle proposte dalla spec di progetto, ancora da confermare.
- Il codice leggibile dell'evento è generato dal sistema (es. regione, anno e progressivo); il formato si decide nel `plan`.
- Il catalogo iniziale dei tipi evento (es. CFM_EG, ROSS, CFA) è caricato con i dati di prova del profilo mock.
- Le regioni sono la lista fissa delle regioni AGESCI (l'elenco esatto si fissa nel `plan`); la zona è un testo libero.
- Gli utenti di prova del mock vanno estesi con almeno una seconda segreteria di un'altra regione per provare il vincolo di Q11; l'`isMemberOf` della segreteria esistente diventa `agesci:PIE:segreteria`.
- Fuori da questa feature: iscrizioni e conteggio reale degli iscritti (fase 3), assegnazione dello staff (FR-020 di progetto), notifiche di modifica e annullamento (fase 4), rimborsi, strutture e sincronizzazione con il censimento, regione preferita dell'utente (FR-008 di progetto), configurazione regionale dei pagamenti.
- Riferimenti alle risorse API (Appendice A della spec di progetto): il contratto OpenAPI si definisce nel `plan`.
