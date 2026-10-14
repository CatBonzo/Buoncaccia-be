# Research: Notifiche e-mail

Decisioni tecniche della feature 004. Formato: Decisione, Perché, Alternative scartate.

## R1. Outbox su MongoDB, relay a Kafka

- **Decisione**: ogni evento di dominio è prima un documento della collezione `outbox` (`status: PENDING`), scritto nella stessa transazione del cambio di stato. Un job `OutboxRelay` (`@Scheduled`, ogni secondo, `buonacaccia.notifications.relay-delay`) legge al più 100 eventi `PENDING` in ordine di creazione, li pubblica su `buonacaccia.domain-events` con chiave = id dell'iscrizione (o dell'evento) e aspetta la conferma del broker (`send(...).get(5 s)`); poi li segna `PUBLISHED`. Se Kafka non risponde il relay si ferma al primo errore e riprova al giro dopo. Gli eventi `PUBLISHED` si cancellano dopo 7 giorni (indice TTL su `publishedAt`).
- **Perché**: NFR-06 ("nessun evento di dominio perso") e US6.1: il cambio di stato non deve fallire se Kafka è fermo, e l'evento non deve perdersi. Pubblicare direttamente su Kafka dopo il salvataggio perderebbe l'evento se l'applicazione cade tra le due scritture. La chiave per iscrizione mantiene l'ordine degli eventi della stessa iscrizione nella stessa partizione.
- **Alternative**: transazioni Kafka + Mongo (non esiste un commit atomico tra i due); Change Streams di MongoDB come CDC (più infrastruttura, resume token da gestire); eventi interni di Spring `@TransactionalEventListener` (scartata da Mahima il 14/10: Kafka resta).
- **Nota**: il relay può pubblicare due volte lo stesso evento (pubblicato, poi caduta prima di segnarlo). È accettato: il consumer è idempotente (R4).

## R2. Transazione tra cambio di stato ed evento di dominio

- **Decisione**: `MongoTransactionManager` e un `TransactionTemplate`; in `EnrollmentService` il metodo `save(enrollment, events...)` salva l'iscrizione e scrive gli eventi in `outbox` nella stessa transazione. Le operazioni sulla capienza (`event_capacity`) restano fuori, come nella fase 3, con le stesse compensazioni.
- **Perché**: il replica set c'è già (fase 0) e la transazione tocca solo l'iscrizione e un documento nuovo dell'outbox: nessun documento "caldo" condiviso, quindi niente conflitti di scrittura anche con 200 iscrizioni contemporanee. La capienza resta atomica con `findAndModify` come prima.
- **Alternative**: outbox incorporato nel documento dell'iscrizione (array `pendingEvents`), atomico senza transazioni ma con un relay che deve scandire tutte le iscrizioni; transazione anche sulla capienza (conflitti `WriteConflict` sul documento della capienza con molte iscrizioni insieme, da ritentare).

## R3. Un solo topic, eventi come snapshot

- **Decisione**: topic `buonacaccia.domain-events` (3 partizioni in locale) con messaggi JSON `DomainEvent { id, type, occurredAt, enrollment?, event, data }`. `enrollment` ed `event` sono snapshot dei dati che servono alle e-mail (numero, partecipante, presentatore, posizione, posizione in lista, adempimenti da fare, scadenza, quota, rimborso, motivo; codice, titolo, date, luogo). Tipi: `ENROLLMENT_REGISTERED`, `AUTHORIZATION_REQUESTED`, `ENROLLMENT_COMPLETED`, `ENROLLMENT_EXPIRED`, `ENROLLMENT_WITHDRAWN`, `ENROLLMENT_PROMOTED`, `ENROLLMENT_REJECTED`, `ENROLLMENT_CANCELLED`, `DEADLINE_REMINDER`, `EVENT_CHANGED`.
- **Perché**: FR-003 chiede di comporre le e-mail con i dati del momento del fatto, non con quelli attuali. Un topic solo basta per un consumer solo; i tipi si distinguono nel messaggio.
- **Alternative**: un topic per tipo (più configurazione senza vantaggi con un consumer); messaggio con il solo id (il consumer rileggerebbe uno stato già cambiato).
- **Autorizzazioni**: `AUTHORIZATION_REQUESTED` è un evento a sé, uno per ogni adempimento di autorizzazione da dare, scritto insieme a `ENROLLMENT_REGISTERED` (o a `ENROLLMENT_PROMOTED` se l'autorizzazione nasce allora). Così l'e-mail ai capi non dipende dal modello di conferma.

## R4. Consumer idempotente e registro

- **Decisione**: `NotificationDispatcher` riceve l'evento, calcola i destinatari (R6) e per ognuno inserisce una `Notification` con indice unico `{domainEventId, recipientId, template}` in stato `PENDING`. Se l'inserimento trova un duplicato: se la notifica è `SENT`, `NO_ADDRESS` o `NO_RECIPIENT` si salta; se è `PENDING` o `FAILED` si ritenta l'invio. Dopo l'invio: `SENT` con `sentAt`. Senza indirizzo: `NO_ADDRESS`, nessun invio. Senza nessun capo trovato: una notifica `NO_RECIPIENT` con `recipientId = "-"`.
- **Perché**: FR-004 e SC-002 (stesso evento due volte → una e-mail). La chiave unica fa da "già fatto" e da registro (FR-006) insieme.
- **Alternative**: tabella separata degli id già elaborati (due scritture per lo stesso scopo); idempotenza solo sull'id del messaggio (un errore a metà tra due destinatari perderebbe il secondo o rimanderebbe il primo).
- **Limite accettato**: se l'applicazione cade dopo l'invio SMTP e prima di segnare `SENT`, alla riconsegna l'e-mail parte di nuovo (at-least-once verso il server di posta). Non esiste un commit atomico con SMTP.

## R5. Ritentativi e dead letter

- **Decisione**: `DefaultErrorHandler` di Spring Kafka con `ExponentialBackOff` (1 s, ×2, al più 4 tentativi in tutto, proprietà `buonacaccia.notifications.retry.*`) e `DeadLetterPublishingRecoverer` verso `buonacaccia.domain-events.DLT`. Il recoverer, prima di pubblicare sul DLT, segna `FAILED` (con `lastError` e `attempts`) le notifiche dell'evento rimaste `PENDING`. Un errore di invio SMTP fa fallire tutto il messaggio; le notifiche già `SENT` alla riconsegna si saltano (R4).
- **Perché**: FR-005 e US6.2–6.3. Il backoff dentro il consumer (bloccante) è accettabile con un solo consumer e pochi messaggi; i ritentativi non bloccanti con topic di retry sono più adatti a grandi volumi.
- **Alternative**: `@RetryableTopic` (tre topic in più); ritentativi con Resilience4j attorno all'invio (duplicherebbe ciò che Spring Kafka fa già, e il DLT andrebbe scritto a mano).
- **Il DLT** non ha un consumer: i messaggi lì si esaminano a mano (fuori ambito una pagina di rinvio, come dice la spec).

## R6. Destinatari

- **Decisione**: `RecipientResolver` per tipo di evento:
  - partecipante: `participantId`; presentatore: `presenterId` se presente e diverso dal partecipante;
  - autorizzazione del capo unità: utenti con `census.unitId = participantUnitId` e ruolo di capo (`MemberType.LEADER`), escluso il partecipante;
  - autorizzazione del capo gruppo: utenti con `census.groupId = participantGroupId` e `census.groupLeader = true`, escluso il partecipante;
  - `EVENT_CHANGED`: partecipanti delle iscrizioni attive dell'evento al momento dell'elaborazione.
  - L'indirizzo è `User.email` letto al momento dell'invio.
- **Perché**: stesse relazioni già usate da `EnrollmentAccessPolicy.canAuthorize` nella fase 3 (chi può autorizzare dal sito riceve il link). Per `EVENT_CHANGED` leggere gli iscritti attivi al momento dell'elaborazione è sufficiente: chi si iscrive dopo vede già i dati nuovi; chi si ritira nel frattempo non ha più bisogno dell'avviso.
- **Annullamento dell'evento**: non usa `EVENT_CHANGED`; `cancelForEvent` della fase 3 chiude ogni iscrizione e ognuna produce il suo `ENROLLMENT_CANCELLED` con motivo e rimborso (FR-013), così il destinatario è esatto.

## R7. Testi delle e-mail

- **Decisione**: `MailComposer` con un metodo per modello (`NotificationTemplate`), testo semplice in italiano costruito con text block e `formatted`; date con `DateTimeFormatter` `dd/MM/yyyy` e ore `HH:mm` in `Europe/Rome`. Oggetto sempre con il numero dell'iscrizione o il codice dell'evento (FR-014). Mittente da `buonacaccia.notifications.from` (`noreply@buonacaccia.local`).
- **Perché**: 12 modelli corti; un motore di template (Thymeleaf) è una dipendenza in più per testi di 5–10 righe. Il testo semplice si verifica facilmente nei test con l'API di Mailpit.
- **Alternative**: Thymeleaf con HTML (più bello, da valutare quando ci sarà il frontend); testi in un file di properties (meno leggibile con i parametri).

## R8. Link di autorizzazione

- **Decisione**: token casuale di 32 byte (`SecureRandom`, Base64 URL senza padding) inviato nel link `{frontend-base-url}/autorizza/{token}`; nel database si salva solo lo SHA-256 del token (`AuthorizationLink.tokenHash`, indice unico) con iscrizione, tipo di autorizzazione, capo, `createdAt`, `usedAt`. Valido se: non usato, iscrizione attiva, adempimento ancora `TODO`, scadenza dell'iscrizione non passata (FR-017). L'uso marca `usedAt` con un `findAndModify` condizionato su `usedAt: null` (un solo uso anche con due clic insieme) e poi chiama l'approvazione o il diniego della fase 3 a nome del capo.
- **Perché**: la validità dipende comunque dallo stato (adempimento ancora da fare, iscrizione attiva), quindi serve un documento: una firma HMAC o un JWT non aggiungerebbe nulla e non sarebbe revocabile da sola. L'hash nel database impedisce di usare i link a chi legge il database. "Link firmato" della spec di progetto (FR-028) è reso così: non indovinabile, non alterabile, monouso.
- **Alternative**: JWT firmato con scadenza (non monouso senza un registro, non revocabile quando un altro capo autorizza); token in chiaro nel database.
- **Risposta per link non valido**: sempre 404 `40420` con lo stesso messaggio, che sia inesistente, usato o scaduto (FR-018). Il 409 `40920` resta per il caso di un link valido in lettura che, al momento della decisione, non lo è più per una corsa (es. un altro capo ha appena autorizzato).

## R9. Promemoria

- **Decisione**: il job `EnrollmentJobs` (ogni 5 minuti) chiama anche `sendReminders()`: per le iscrizioni `REGISTERED` con adempimenti da fare e scadenza entro 3 giorni di calendario (Rome), se in `remindersSent` manca la chiave `D3:<scadenza>` la aggiunge (update condizionato) e scrive un `DEADLINE_REMINDER` nella stessa transazione; lo stesso per `D1:<scadenza>` entro 1 giorno. La chiave contiene la scadenza, così dopo una promozione con nuova scadenza i promemoria ripartono (US3.4).
- **Perché**: riusa il job della fase 3 senza un secondo scheduler. "3 giorni prima" = la data di calendario della scadenza meno 3, all'ora italiana (edge case dell'ora legale).
- **Alternative**: un job dedicato a orario fisso (es. alle 08:00): più "umano" per chi legge, ma un'iscrizione registrata a 2 giorni dalla scadenza non riceverebbe mai il D3; con il job ogni 5 minuti riceve subito il D3 poi il D1 al momento giusto. Iscrizioni con meno di 1 giorno alla prima corsa ricevono solo il D1.

## R10. Modifica dell'evento

- **Decisione**: `EventService.update` confronta `startDate`, `endDate` e `location` prima e dopo; se l'evento è pubblicato (non bozza) e almeno uno è cambiato, scrive `EVENT_CHANGED` con i valori nuovi nella stessa transazione del salvataggio dell'evento. Le altre modifiche non producono eventi (FR-012).
- **Perché**: sono i dati che fanno cambiare i piani a chi partecipa; posti e descrizione no.

## R11. Test

- **Decisione**: `AbstractTest` avvia anche Kafka (`org.testcontainers.kafka.KafkaContainer`, immagine `apache/kafka:4.3.1` come nel compose) e Mailpit (`GenericContainer` `axllent/mailpit:v1.31.5`) una volta per la suite. `MailpitClient` legge `/api/v1/messages` e `/api/v1/message/{id}` e cancella la casella prima di ogni test. Le attese usano Awaitility. Per US6 il relay e l'invio si fermano con flag di test (`OutboxRelay.pause()`, SMTP puntato a una porta chiusa via `JavaMailSenderImpl.setPort`) invece di fermare i container, che sono condivisi.
- **Perché**: le prove su "nessuna e-mail persa o doppia" hanno senso solo con un broker e un server di posta veri. Fermare un container condiviso renderebbe instabili gli altri test.
- **Alternative**: Kafka embedded di `spring-kafka-test` (meno fedele, un'altra dipendenza); GreenMail (dipendenza in più, mentre Mailpit è già quello del compose).

## R12. Errori

- **Decisione**: nuovo dominio `NOTIFICATION`: `40420` (404, link non valido o non più valido), `40920` (409, decisione non più possibile). Il motivo mancante nel diniego usa `42200` come le altre validazioni (`@NotBlank` sul DTO).
- **Perché**: FR-020; stesso schema `SSSNN` delle fasi precedenti.
