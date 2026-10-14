# Research: Iscrizioni e lista d'attesa

**Feature**: `003-iscrizioni` | **Data**: 2026-10-14 | **Plan**: [plan.md](plan.md)

Ogni voce riporta la decisione, la motivazione e le alternative scartate. Nessun punto resta aperto; FR-017 segue l'opzione consigliata ("pagamento alla promozione"), in attesa della conferma di Mahima. La base tecnica è la stessa delle feature 001 e 002.

## R1. Capienza dell'evento: documento separato con aggiornamenti atomici condizionati

- **Decisione**: la collezione `event_capacity` ha un documento per evento, con `_id` = id evento e i campi `seats`, `lastArrival`, `inSeats`, `waiting` e `active`.
  - **Iscrizione nei posti**: un solo `findAndModify` con condizione `{_id, waiting: 0, $expr: inSeats < seats}` e `$inc {lastArrival: 1, inSeats: 1, active: 1}`. Se trova il documento, l'iscrizione è nei posti con ordine di arrivo `lastArrival`.
  - **Iscrizione in attesa**: se il primo passo non trova il documento, un secondo `findAndModify` su `{_id}` con `$inc {lastArrival: 1, waiting: 1, active: 1}`; l'iscrizione è in attesa.
  - **Uscita**: decrementa `inSeats` (o `waiting`) e `active`.
  - **Promozione**: `findAndModify` con condizione `{waiting > 0, $expr: inSeats < seats}`, `$inc {inSeats: 1, waiting: -1}`, seguito dal passaggio a "nei posti" della prima iscrizione in attesa.
- **Motivazione**:
  - Ogni passo è un'operazione atomica su un solo documento, quindi l'ordine di arrivo è unico e crescente anche con 200 richieste nello stesso secondo (FR-005, SC-002, §2.6.6 della spec di progetto).
  - La condizione `waiting: 0` impedisce che un nuovo arrivato occupi il posto appena liberato prima della promozione di chi aspetta (BR-08).
  - Il documento è separato dall'evento perché l'evento usa `@Version` e viene salvato per intero dalla segreteria: se i contatori fossero sull'evento, una modifica della segreteria cancellerebbe gli incrementi arrivati nel frattempo, oppure ogni iscrizione farebbe fallire le sue modifiche.
- **Alternative**:
  - Transazioni MongoDB multi-documento: il replica set le consente, ma sul picco delle 09:00 i conflitti di scrittura sullo stesso evento richiedono tentativi ripetuti, ed è più codice.
  - Contatori sul documento evento: scartati per il motivo detto sopra.
  - Conteggio delle iscrizioni a ogni richiesta: dà doppi posti sotto carico.

## R2. Contatore iscritti nel catalogo letto dalla capienza

- **Decisione**: il campo `enrolledCount` esce dal documento `Event`. Il numero di iscritti del DTO viene da `event_capacity.active`, letto per tutta la pagina con una sola query.
  - I filtri di disponibilità della ricerca (APERTE, LIMITATE, SOVRANNUMERO_PESANTE) cercano prima nella capienza gli id degli eventi con il rapporto giusto, poi filtrano gli eventi con `_id in [...]`.
  - Un evento senza documento di capienza ha 0 iscritti.
- **Motivazione**: c'è una sola fonte per il numero di iscritti, senza copie da tenere allineate; la fase 2 diceva già "sempre 0 finché le iscrizioni non arrivano".
- **Alternative**: copiare il contatore sull'evento con `$inc` (ha lo stesso problema di R1 con il salvataggio della segreteria); `$lookup` nell'aggregazione di ricerca (più complesso da paginare e contare).

## R3. Unicità dell'iscrizione attiva e numero leggibile

- **Decisione**:
  - Un indice univoco parziale `{eventId, participantId}` con filtro `active: true` è la rete di sicurezza contro due iscrizioni attive dello stesso socio allo stesso evento. Il controllo esplicito prima della prenotazione dà il messaggio parlante: 409 `40911`.
  - Il numero leggibile è `<codice evento>-<ordine di arrivo su 4 cifre>`, es. `PIE-2027-001-0031`: è univoco perché l'ordine di arrivo è univoco per evento.
  - Se l'inserimento dell'iscrizione fallisce dopo la prenotazione (due richieste identiche nello stesso istante), i contatori vengono rimessi a posto. Resta un buco nell'ordine di arrivo, ma solo in questo caso: con soci diversi non ci sono buchi (SC-002).
- **Motivazione**: §2.6.6 della spec di progetto (indice univoco parziale); un numero leggibile da dire al telefono alla segreteria (FR-006).
- **Alternative**: un contatore globale dei numeri, scartato perché è un secondo punto di contesa.

## R4. Chi è il partecipante e chi lo può iscrivere

- **Decisione**:
  - **Iscrizione autonoma** (`POST /api/v1/events/{id}/enrollments` senza corpo): il partecipante è la persona del token.
  - **Iscrizione tramite capo** (corpo `{ "memberCode": "..." }`): il partecipante è il socio con quel codice e il presentatore è chi chiama, che deve essere un capo (`memberType = LEADER`) con la stessa `unitId` del ragazzo.
  - In entrambi i casi serve l'entitlement `iscrizione:self`, che è il permesso dei soci (AUTH-1); la relazione capo-unità è il controllo AUTH-2.
  - Le regole di BR-02 e della modalità dell'evento:

| Chi chiama | Branca del partecipante | Modalità dell'evento ammesse |
| --- | --- | --- |
| Partecipante stesso | RS o capo (CO) | `SELF`, `BOTH` |
| Capo della stessa unità | qualsiasi (tipicamente LC, EG) | `BY_LEADER`, `BOTH` |

  - L/C ed E/G non possono iscriversi da soli: 422 con il motivo.
- **Motivazione**: §2.2 della spec di progetto (relazione con la risorsa) e BR-02; il codice socio è quello che il capo conosce.
- **Alternative**: un entitlement dedicato `iscrizione:presentatore` (non c'è nella lista confermata in Q15).

## R5. Dati del socio usati: data di nascita e capo gruppo

- **Decisione**:
  - `User` riceve il campo `birthDate` (`LocalDate`), come nella spec di progetto §2.3.3.
  - `Census` riceve `groupLeader` (boolean).
  - Il mock li legge da `mocked-identity.yml`; se nel mock manca l'anno del censimento si usa l'anno corrente, così i dati di prova restano "in regola" anche nel 2027.
  - "In regola" vuol dire `status = IN_GOOD_STANDING` e `year` = anno corrente nell'ora italiana, all'istante dell'iscrizione.
  - Il capo unità del partecipante è un capo con la stessa `unitId`; il capo gruppo è un capo con `groupLeader = true` e la stessa `groupId`.
- **Motivazione**: servono per BR-01, BR-06 (età alla partenza) e FR-009; le strutture complete non sono in questa fase.
- **Alternative**: data di nascita nel token (non è un claim previsto); strutture come collezione (fase "strutture").

## R6. Requisiti BR-06 raccolti tutti in una volta

- **Decisione**: `EnrollmentEligibility` controlla, nell'ordine, i punti sotto e raccoglie tutti i motivi in un elenco `FieldErrorDTO`. Se ce n'è almeno uno risponde 422 `42210` (dominio `ENROLLMENT`), come per gli invarianti della fase 2.
  - censimento presente e in regola
  - branca e modalità (R4)
  - età alla data di partenza tra `minAge` e `maxAge` (data di nascita mancante = motivo)
  - branca tra `branches`
  - tipo socio tra `memberTypes`
  - nessuna altra iscrizione attiva a eventi dello stesso tipo se `oneActivePerType`
  - iscrizioni attive del gruppo a questo evento < `maxPerGroup`

  Stato dell'evento e iscrizione già attiva non sono "requisiti" ma conflitti: 409 `40912` e `40911`.
- **Motivazione**: FR-003 e FR-024 di progetto ("errori parlanti"); la stessa forma della fase 2 per il frontend.
- **Alternative**: fermarsi al primo motivo (più giri per l'utente).
- **Limite noto**: `maxPerGroup` è controllato con un conteggio, quindi due iscrizioni dello stesso gruppo nello stesso istante possono superarlo di uno. È accettato e documentato in AGENTS.md: lo rende esatto solo un contatore per gruppo, che si potrà aggiungere se servirà.

## R7. Adempimenti generati dall'evento

- **Decisione**: alla registrazione, per ogni adempimento dell'evento:

| Tipo | Stato iniziale |
| --- | --- |
| `LEADER_VALIDATION` | `SATISFIED` se l'iscrizione l'ha fatta il presentatore, altrimenti `NOT_REQUIRED` (in questa fase un R/S non indica un presentatore) |
| `UNIT_LEADER_AUTHORIZATION`, `GROUP_LEADER_AUTHORIZATION` | `TODO` |
| `PAYMENT` | `TODO` solo se quota > 0 e iscrizione nei posti (FR-017); con quota > 0 viene aggiunto anche se l'evento non lo elenca (BR-05) |
| `DOCUMENT`, `ELECTRONIC_FORM` | `TODO` |

  - Se non resta niente `TODO` l'iscrizione nasce `COMPLETED`.
  - Alla promozione si aggiunge `PAYMENT`: un'iscrizione `COMPLETED` torna `REGISTERED` e riceve una nuova scadenza a 9 giorni.
  - Le azioni sugli adempimenti sono tre:
    - `approve`, del capo unità o capo gruppo, solo per la propria autorizzazione;
    - `reject`, con motivo obbligatorio: l'iscrizione diventa `REJECTED`;
    - `satisfy`, della segreteria con `iscrizione:gestione` e la regola territoriale, per qualsiasi adempimento, con una nota.
- **Motivazione**: BR-05, FR-007…FR-011; fino alle fasi 5 e 7 la segreteria è l'unica via per pagamento e documenti.
- **Alternative**: pagamento richiesto anche in attesa (è l'altra opzione della domanda a Mahima).

## R8. Scadenze e lista d'attesa alla partenza: job ogni 5 minuti

- **Decisione**: `EnrollmentJobs` è un `@Scheduled(fixedDelay = 5 min, initialDelay = 5 min)` che usa il `Clock`.
  - Le iscrizioni `REGISTERED` con `deadline < now` diventano `EXPIRED`; se erano nei posti, scatta la promozione.
  - Le iscrizioni attive in attesa di eventi con `startsAt <= now` diventano `CANCELLED` con motivo "posti esauriti".
  - I test chiamano i metodi del job direttamente con l'orologio spostato.
- **Motivazione**: FR-012 chiede la scadenza entro 15 minuti; diversamente dallo stato dell'evento (fase 2), qui il passaggio cambia i dati (posti liberati, promozioni) e non si può solo calcolare in lettura. Con una sola istanza non serve ShedLock (principio IV).
- **Alternative**: calcolo in lettura come nella fase 2 (la promozione deve comunque avvenire); ShedLock (serve solo con più istanze).

## R9. Ritiro e diritto al rimborso

- **Decisione**:
  - Il ritiro è ammesso da `REGISTERED` o `COMPLETED` prima di `startsAt` dell'evento, al partecipante o al presentatore.
  - `refundDue = quota > 0 && giorni(oggiRoma, dataPartenza) >= 7`, dove i giorni sono la differenza di calendario tra la data del ritiro e la data di partenza. Esempio: partenza il 20, ritiro il 13 → 7 giorni → rimborso (BR-10, Q12).
  - Il rimborso effettivo, e il controllo se il pagamento c'è stato, sono della fase 5.
  - All'annullamento dell'evento tutte le attive diventano `CANCELLED` con `refundDue = quota > 0`.
- **Motivazione**: BR-09, BR-10, FR-019…FR-020.

## R10. Modifica dei posti dell'evento

- **Decisione**: quando la segreteria cambia `seats` con il `PUT` della fase 2, `EventService` chiama `EventCapacityService.updateSeats`.
  - La riduzione viene rifiutata se i posti nuovi sono meno di `inSeats`: 409 `40905` (dominio CATALOG).
  - In caso di aumento, i posti della capienza si aggiornano e si promuovono gli iscritti in attesa.
  - Alla pubblicazione si crea il documento di capienza, con `$setOnInsert` per sicurezza.
- **Motivazione**: FR-014 (FR-037 di progetto).

## R11. Nuovi codici di errore

- **Decisione**: nuovo dominio `ENROLLMENT`:
  - `40910` transizione dell'iscrizione non consentita
  - `40911` iscrizione già attiva
  - `40912` iscrizioni dell'evento non aperte
  - `42210` requisiti non soddisfatti, con l'elenco dei motivi

  Nel dominio CATALOG si aggiunge `40905` posti sotto gli iscritti nei posti. Il messaggio di `40903` diventa generico ("La risorsa è stata modificata da qualcun altro…"), perché ora vale anche per le iscrizioni.
- **Motivazione**: stessa forma della fase 2 (R7 della 002); codici distinti aiutano il frontend.
