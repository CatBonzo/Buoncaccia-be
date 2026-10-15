# Feature Specification: Pagamenti e rimborsi registrati dalla segreteria

**Feature Branch**: `005-pagamenti`

**Created**: 2026-10-15

**Status**: Draft

**Input**: User description: "Fase 5 – Pagamenti e rimborsi (spec di progetto v2.0: BR-10, BR-11, FR-039, FR-041, FR-042, FR-043, FR-045, FR-046, §2.5.3, §2.5.5, §2.5.6; Planner v2: bonifico registrato dalla segreteria, senza caricamento della ricevuta). Il socio vede quanto deve, a chi e come pagare. La segreteria che organizza l'evento registra i pagamenti ricevuti per bonifico o in contanti; quando i pagamenti coprono la quota l'adempimento di pagamento è soddisfatto e l'iscrizione può completarsi. Quando un'iscrizione pagata si chiude con diritto al rimborso, nasce una richiesta di rimborso che la segreteria lavora a mano (presa in carico, emesso o rifiutato). Il socio è avvisato per e-mail a ogni passaggio. La segreteria vede la riconciliazione di un evento. Fuori ambito: pagamento elettronico con PayPal o carta e rimborsi automatici (non c'è un gateway), caricamento della ricevuta di bonifico (documenti, fase successiva), candidature, Keycloak."

## Clarifications

### Session 2026-10-15

- Il pagamento elettronico (FR-040, FR-044) entra in questa fase? → A: no. Nel locale non c'è un gateway e il Planner v2 prevede in questa fase solo il bonifico registrato dalla segreteria; il pagamento elettronico si aggiungerà dopo, riusando lo stesso registro dei pagamenti.
- Il socio carica la ricevuta del bonifico? → A: no, in questa fase è la segreteria a registrare il pagamento quando lo vede arrivare. Il caricamento dei file è della fase dei documenti.
- Pagamento in più rate o di importo diverso dalla quota (§2.5.3)? → A (confermato da Mahima il 15/10): più bonifici sulla stessa iscrizione, l'adempimento si soddisfa quando il totale registrato raggiunge la quota; un totale che superi la quota è rifiutato.
- Chi ha pagato solo in parte e vede scadere l'iscrizione ha diritto al rimborso (§2.5.5)? → A (deciso da Mahima il 15/10): no. Il rimborso c'è solo se la persona si ritira almeno 7 giorni prima della partenza (BR-10) e lo fa lei (o il suo presentatore); il diniego di un'autorizzazione e l'annullamento dell'evento restano con rimborso del versato.
- Contanti e PayPal? → A (Mahima, 15/10): il pagamento è solo bonifico; PayPal (simulato) e Keycloak vengono dopo il completamento di backend e frontend.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Sapere quanto devo pagare e a chi (Priority: P1)

Il socio, dalla pagina dell'iscrizione, vede la quota, quanto ha già versato, quanto manca e le coordinate per il bonifico della segreteria che organizza l'evento (IBAN e contatti).

**Why this priority**: senza le coordinate il bonifico non si può fare; è anche l'unica parte che non richiede ancora la segreteria per essere provata.

**Independent Test**: iscrivere un socio a un evento con quota e leggere l'iscrizione: quota, versato 0, residuo uguale alla quota, IBAN e contatti della regione dell'evento.

**Acceptance Scenarios**:

1. **Given** un'iscrizione nei posti a un evento con quota maggiore di 0, **When** il socio la apre, **Then** vede quota, versato, residuo e IBAN e contatti della segreteria della regione dell'evento.
2. **Given** un'iscrizione in lista d'attesa, **When** il socio la apre, **Then** vede la quota ma nessuna richiesta di pagamento finché non è promosso (decisione del 14/10).
3. **Given** un evento gratuito, **When** il socio apre l'iscrizione, **Then** non vede nessuna sezione di pagamento.
4. **Given** una regione senza IBAN configurato, **When** il socio apre l'iscrizione, **Then** vede solo i contatti e l'indicazione di chiedere le coordinate alla segreteria.

---

### User Story 2 - La segreteria registra un pagamento (Priority: P1)

La segreteria che organizza l'evento registra un pagamento ricevuto (solo bonifico, niente contanti) indicando importo, data e riferimento (es. CRO). Se i pagamenti registrati coprono la quota, l'adempimento di pagamento è soddisfatto e, se non resta altro, l'iscrizione si completa. Il socio riceve una e-mail.

**Why this priority**: è l'azione che manda avanti le iscrizioni; senza, i posti pagati restano "da fare" fino alla scadenza.

**Independent Test**: con un'iscrizione che ha solo il pagamento da fare, registrare l'intera quota con il token della segreteria e verificare che l'iscrizione sia completa e che il socio abbia ricevuto l'e-mail.

**Acceptance Scenarios**:

1. **Given** un'iscrizione con pagamento da fare, **When** la segreteria registra un importo pari alla quota, **Then** l'adempimento è soddisfatto, l'iscrizione si completa se non resta altro e il socio riceve la conferma del pagamento.
2. **Given** lo stesso caso, **When** la segreteria registra un importo minore, **Then** il pagamento è registrato, l'adempimento resta da fare e il residuo si aggiorna.
3. **Given** un pagamento parziale già registrato, **When** la segreteria registra il resto, **Then** l'adempimento è soddisfatto.
4. **Given** un importo che farebbe superare la quota, **When** la segreteria lo registra, **Then** è rifiutato con l'indicazione del residuo e non cambia nulla.
5. **Given** una segreteria di un'altra regione o un socio, **When** tentano di registrare un pagamento, **Then** non possono (stessa regola territoriale della gestione dell'evento).
6. **Given** un'iscrizione chiusa (scaduta, ritirata, rifiutata, annullata), **When** la segreteria prova a registrare un pagamento, **Then** è rifiutato: il versamento arrivato tardi si gestisce come rimborso (storia 3).
7. **Given** un pagamento registrato per errore, **When** la segreteria lo annulla con un motivo, **Then** il pagamento risulta annullato, il versamento si ricalcola e l'adempimento torna da fare se la quota non è più coperta.

---

### User Story 3 - Il rimborso nasce da solo e la segreteria lo lavora (Priority: P1)

Quando un'iscrizione con pagamenti registrati si chiude con diritto al rimborso, il sistema crea una richiesta di rimborso dell'importo versato. La segreteria la prende in carico, poi la segna emessa (con data e riferimento) o la rifiuta con un motivo. Il socio è avvisato a ogni passaggio.

**Why this priority**: BR-10 e il diritto al rimborso sono già calcolati dalla fase 3 ma oggi non portano a nulla; chiude il ciclo del denaro.

**Independent Test**: registrare il pagamento, ritirarsi 10 giorni prima della partenza, trovare la richiesta nella coda della segreteria, segnarla emessa e leggere le e-mail.

**Acceptance Scenarios**:

1. **Given** un'iscrizione pagata, **When** il socio si ritira almeno 7 giorni prima della partenza, **Then** nasce un rimborso RICHIESTO dell'importo versato e il socio riceve la notizia.
2. **Given** lo stesso caso a meno di 7 giorni dalla partenza, **When** il socio si ritira, **Then** non nasce nessun rimborso.
3. **Given** un'iscrizione pagata solo in parte che scade, **When** si chiude, **Then** non nasce nessun rimborso (deciso da Mahima il 15/10); **Given** un diniego di un'autorizzazione con importo versato, **Then** nasce un rimborso del versato (default, non toccato dalla decisione).
4. **Given** un evento annullato, **When** si annullano le iscrizioni, **Then** ogni iscrizione con pagamenti registrati ottiene un rimborso totale; chi era in attesa non ha pagato e non ha nulla da rimborsare.
5. **Given** una richiesta RICHIESTO, **When** la segreteria la prende in carico, **Then** diventa IN_LAVORAZIONE; **When** la segna emessa con data e riferimento, **Then** diventa EMESSO e il socio riceve l'e-mail di conferma.
6. **Given** una richiesta non lavorabile (es. coordinate non valide), **When** la segreteria la rifiuta con un motivo, **Then** diventa RIFIUTATO e il socio riceve il motivo.
7. **Given** una richiesta già EMESSO o RIFIUTATO, **When** si tenta un'altra transizione, **Then** è rifiutata.
8. **Given** una iscrizione senza pagamenti registrati, **When** si chiude con diritto al rimborso, **Then** non nasce nessuna richiesta.

---

### User Story 4 - Quadro dei pagamenti di un evento (Priority: P2)

La segreteria vede, per un evento, l'elenco dei pagamenti e dei rimborsi con totali: incassato, annullato, da rimborsare, rimborsato, e le iscrizioni attive che devono ancora pagare. Può scaricare l'elenco in formato CSV.

**Why this priority**: serve a chiudere i conti dell'evento (FR-046) ma nessuna altra storia ne dipende.

**Independent Test**: con tre pagamenti e un rimborso su un evento, leggere i totali e controllare che il CSV contenga le stesse righe.

**Acceptance Scenarios**:

1. **Given** un evento con pagamenti e rimborsi, **When** la segreteria apre la riconciliazione, **Then** vede i totali e le iscrizioni attive con residuo maggiore di 0.
2. **Given** la stessa richiesta con formato CSV, **When** la segreteria la scarica, **Then** riceve una riga per pagamento e per rimborso con numero iscrizione, importo, metodo, stato e date.
3. **Given** la coda dei rimborsi, **When** la segreteria la apre filtrando per stato, **Then** vede le richieste della sua regione in ordine di data.

---

### User Story 5 - Coordinate di pagamento della regione (Priority: P3)

L'amministrazione o la segreteria della regione impostano IBAN, intestatario, contatti e orari della segreteria; i soci li vedono nelle loro iscrizioni a eventi di quella regione.

**Why this priority**: è dato di configurazione; la storia 1 funziona anche con le coordinate inserite dall'amministrazione una volta.

**Independent Test**: impostare l'IBAN di una regione e leggerlo nell'iscrizione a un evento di quella regione.

**Acceptance Scenarios**:

1. **Given** la segreteria della regione PIE, **When** imposta IBAN, intestatario e contatti, **Then** sono salvati e visibili a chi ha un'iscrizione a un evento PIE.
2. **Given** un IBAN non valido, **When** lo si salva, **Then** è rifiutato con l'indicazione del campo.
3. **Given** la segreteria di un'altra regione, **When** prova a cambiare le coordinate della regione PIE, **Then** non può.
4. **Given** un evento nazionale, **When** il socio apre l'iscrizione, **Then** vede le coordinate dell'amministrazione nazionale.

---

### Edge Cases

- Due registrazioni dello stesso pagamento nello stesso istante (doppio clic): la seconda è rifiutata come doppione se ha lo stesso riferimento e importo sulla stessa iscrizione.
- La quota dell'evento cambia dopo la registrazione di un pagamento: i pagamenti già registrati restano; l'importo dovuto resta quello della quota al momento della registrazione dell'iscrizione.
- Un pagamento registrato su un'iscrizione in attesa: rifiutato (chi è in attesa non deve ancora pagare).
- Promozione dalla lista d'attesa con la quota già coperta: non si aggiunge nessun pagamento da fare.
- Rimborso di un'iscrizione con un pagamento annullato: si rimborsa solo il versato valido.
- Iscrizione ritirata o scaduta, poi un bonifico che arriva in ritardo: il sistema non lo registra (FR-004); si gestisce fuori dal sistema e non c'è un tipo di pagamento "fuori iscrizione" in questa fase.
- Due segreterie lavorano la stessa richiesta insieme: la seconda transizione fallisce con conflitto di stato.

## Requirements *(mandatory)*

### Pagamenti

- **FR-001**: L'iscrizione MUST mostrare, per chi ha l'adempimento di pagamento, la quota, il totale registrato, il residuo e le coordinate di pagamento della segreteria che organizza l'evento (FR-039, BR-11).
- **FR-002**: La segreteria che gestisce l'evento (stessa regola della fase 2) MUST poter registrare un pagamento con importo, metodo (solo `BONIFICO`, facoltativo), data di ricevimento e riferimento facoltativo (FR-041).
- **FR-003**: L'importo MUST essere maggiore di 0 e non superare il residuo; un doppione (stessa iscrizione, importo e riferimento non vuoto) MUST essere rifiutato.
- **FR-004**: La registrazione MUST essere possibile solo su iscrizioni attive nei posti con l'adempimento di pagamento da fare.
- **FR-005**: Quando il totale dei pagamenti validi raggiunge la quota, l'adempimento di pagamento MUST diventare soddisfatto e l'iscrizione completarsi se non resta altro (stessa logica della fase 3, FR-030 di progetto).
- **FR-006**: La segreteria MUST poter annullare un pagamento registrato, con un motivo; il totale si ricalcola e, se la quota non è più coperta su un'iscrizione ancora attiva, l'adempimento torna da fare.
- **FR-007**: Un pagamento MUST conservare chi l'ha registrato e quando.
- **FR-008**: Alla registrazione di un pagamento il socio MUST ricevere un'e-mail con importo, residuo e (se completa) la conferma.

### Rimborsi

- **FR-009**: Alla chiusura di un'iscrizione con pagamenti validi il sistema MUST creare una richiesta di rimborso dell'importo versato, se il diritto c'è: ritiro della persona secondo BR-10 (FR-043), diniego di un'autorizzazione, annullamento dell'evento (Clarifications).
- **FR-010**: Il rimborso MUST attraversare gli stati `RICHIESTO` → `IN_LAVORAZIONE` → `EMESSO` oppure `RIFIUTATO` (con motivo); le transizioni fuori da questo schema MUST essere rifiutate; gli stati finali non cambiano.
- **FR-011**: La segreteria che gestisce l'evento MUST poter vedere la coda dei rimborsi, filtrarla per stato e per evento e lavorare le richieste (FR-045); `EMESSO` richiede data e riferimento facoltativo.
- **FR-012**: A ogni passaggio (richiesta, emesso, rifiutato) il socio MUST ricevere un'e-mail; il presentatore, se c'è, riceve le stesse.
- **FR-013**: Il rimborso e il calcolo del diritto MUST usare il valore già deciso alla chiusura dell'iscrizione (fase 3); chi non ha pagato non genera richieste.

### Riconciliazione

- **FR-014**: La segreteria MUST poter leggere, per un evento, i totali (incassato, annullato, da rimborsare, rimborsato) e le iscrizioni attive con residuo (FR-046).
- **FR-015**: La stessa riconciliazione MUST essere scaricabile in CSV con una riga per pagamento e per rimborso.

### Coordinate della regione

- **FR-016**: Per ogni regione MUST esistere una configurazione con intestatario, IBAN, contatti e orari; la segreteria della regione e l'amministrazione MUST poterla modificare (FR-042).
- **FR-017**: L'IBAN MUST essere validato (formato e cifre di controllo) prima di salvarlo.
- **FR-018**: Gli eventi nazionali MUST usare la configurazione dell'amministrazione nazionale.

### Errori

- **FR-019**: Gli errori delle nuove funzioni MUST usare l'envelope e i codici delle fasi precedenti, con status HTTP significativo: 404 per risorsa inesistente o fuori dalla propria regione, 409 per operazione non consentita nello stato attuale, 422 per importo o IBAN non validi.

### Key Entities

- **Pagamento**: un versamento registrato dalla segreteria su un'iscrizione (importo, metodo, data, riferimento, stato valido o annullato, chi e quando, motivo di annullamento).
- **Rimborso**: richiesta di restituire all'iscritto l'importo versato (importo, stato, motivo della chiusura, data di emissione, riferimento, motivo del rifiuto).
- **Configurazione regionale**: coordinate di pagamento e contatti della segreteria di una regione (o dell'amministrazione nazionale).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Il socio vede quota, versato, residuo e coordinate di pagamento in una sola lettura dell'iscrizione, per il 100% delle iscrizioni nei posti con quota.
- **SC-002**: La segreteria completa la registrazione di un pagamento in una sola richiesta e il socio riceve la conferma per e-mail entro 10 secondi in locale.
- **SC-003**: Nei test, per ogni modo di chiusura con diritto al rimborso nasce esattamente una richiesta, con l'importo versato; nessuna per chi non ha pagato.
- **SC-004**: Il totale incassato meno il totale rimborsato della riconciliazione coincide con la somma dei pagamenti validi meno i rimborsi emessi, su qualunque sequenza di operazioni provata.
- **SC-005**: Nessuna transizione di rimborso fuori schema viene accettata (100% dei casi provati).

## Assumptions

- La segreteria che registra è quella che ha l'entitlement `pagamento:registrazione` (già nel catalogo della fase 1) e che gestisce l'evento secondo la regola territoriale della fase 2; l'amministrazione vale per tutti gli eventi.
- Le coordinate della regione sono lette da una configurazione per regione; se manca, il socio vede solo un invito a contattare la segreteria.
- Il diritto al rimborso alla chiusura è quello già calcolato e conservato nell'iscrizione dalla fase 3 (BR-10); questa fase lo applica quando ci sono pagamenti e lo estende al diniego; la scadenza non dà mai rimborso (Clarifications).
- Le e-mail passano dall'outbox e da Kafka della fase 4; nessun nuovo canale.
- Gli importi sono in euro, con due decimali.
- Fuori ambito: pagamento con PayPal o carta e rimborso automatico (nessun gateway in locale); caricamento della ricevuta e documenti; pagamenti non legati a un'iscrizione; esportazioni contabili oltre il CSV; storno di un rimborso già emesso.
