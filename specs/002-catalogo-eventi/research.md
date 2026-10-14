# Research: Catalogo eventi

**Feature**: `002-catalogo-eventi` | **Data**: 2026-10-13 | **Plan**: [plan.md](plan.md)

Ogni voce: decisione, motivazione, alternative scartate. Nessun punto resta aperto. Base tecnica invariata rispetto alla feature 001 (Spring Boot 4.1.1, MongoDB, Lombok, MapStruct, guardia `@SecurityAccess`, envelope, circuit breaker, Testcontainers).

## R1. Stati che dipendono dal tempo: calcolati, non aggiornati da un job

- **Decisione**: sull'evento si salvano solo lo **stato di ciclo di vita** scelto dalle persone (`DRAFT`, `PUBLISHED`, `CANCELLED`) e, se c'è, l'istante della chiusura anticipata delle iscrizioni. Alla creazione e a ogni modifica si calcolano e si salvano anche quattro istanti in UTC: apertura iscrizioni (giorno di apertura, 09:00 Europe/Rome), chiusura iscrizioni (giorno di chiusura, 23:59:59.999 Europe/Rome), inizio (00:00 Europe/Rome del giorno di partenza), fine (23:59:59.999 Europe/Rome del giorno di rientro). Lo **stato effettivo** (`PUBLISHED`, `REGISTRATION_OPEN`, `REGISTRATION_CLOSED`, `IN_PROGRESS`, `COMPLETED`) è una funzione pura di questi dati e dell'ora corrente, calcolata a ogni lettura (`EventStatusResolver`). La ricerca per stato traduce lo stato in condizioni sugli istanti.
- **Motivazione**: FR-013/FR-014 chiedono che lo stato sia **sempre** coerente con l'ora: calcolandolo non c'è mai un ritardo, nemmeno di un minuto, e non serve un job pianificato da monitorare (principio IV). Gli istanti salvati gestiscono il cambio dell'ora legale una volta sola, quando si salvano, con `ZonedDateTime` su `Europe/Rome`. L'ora corrente viene da un bean `Clock`, sostituibile nei test (US3).
- **Alternative**: job `@Scheduled` ogni minuto che aggiorna lo stato in MongoDB (più codice, stato salvato che può essere vecchio, test con attese); stato calcolato senza istanti salvati (la ricerca per stato non si potrebbe fare con una query).

## R2. Regione della segreteria da `isMemberOf`

- **Decisione**: la regione si ricava dal claim `isMemberOf` del token cercando un valore della forma `agesci:<SIGLA>:segreteria` con `<SIGLA>` nella lista fissa delle regioni (R3). Il claim può essere una stringa o una lista: si considerano tutti i valori. Il controllo sta in `service.guard.EventAccessPolicy`: la guardia `@SecurityAccess` verifica l'entitlement `evento:gestione` (AUTH-1); la policy verifica la relazione con l'evento (AUTH-2): chi ha il ruolo `agesci:sistema:amministratore` gestisce tutto; gli altri solo eventi regionali o di zona della propria regione; gli eventi nazionali solo l'amministrazione. Regione mancante o sconosciuta: 403.
- **Motivazione**: scelta di Mahima (clarify del 13/10). La regola dipende dal singolo evento, quindi non può stare nell'annotazione del controller; tenerla in una classe separata la rende testabile da sola.
- **Alternative**: claim `region` dedicato (scartato da Mahima); regione del censimento (non vale per chi non è censito).
- **Effetto sul mock**: in `mocked-identity.yml` la segreteria esistente passa a `isMemberOf: agesci:PIE:segreteria`; si aggiunge `segreteria-lig` (user-005, `agesci:LIG:segreteria`) per provare il rifiuto fra regioni. L'amministrazione resta `agesci:socio:false`.

## R3. Regioni come enum

- **Decisione**: enum `Region` con sigla di tre lettere e nome: ABR Abruzzo, BAS Basilicata, CAL Calabria, CAM Campania, EMR Emilia-Romagna, FVG Friuli-Venezia Giulia, LAZ Lazio, LIG Liguria, LOM Lombardia, MAR Marche, MOL Molise, PIE Piemonte, PUG Puglia, SAR Sardegna, SIC Sicilia, TOS Toscana, TAA Trentino-Alto Adige, UMB Umbria, VDA Valle d'Aosta, VEN Veneto. Esposto da `GET /api/v1/regions`. La zona è un testo libero (max 100 caratteri).
- **Motivazione**: FR-022 e il default confermato da Mahima: niente collezione da gestire. Le sigle sono quelle usate in `isMemberOf`.
- **Alternative**: collezione `regions` (serve solo quando arriveranno le strutture dal censimento). **Da verificare con Mahima**: se le sigle ufficiali AGESCI sono diverse, si cambiano qui e nel mock.

## R4. Modifiche concorrenti: versione ottimistica

- **Decisione**: campo `@Version Long version` sul documento `Event`. Ogni richiesta di modifica porta la `version` letta; se non corrisponde, Spring Data lancia `OptimisticLockingFailureException`, che diventa 409 con codice `40903`.
- **Motivazione**: FR-018 senza lock né transazioni; è il meccanismo standard di Spring Data MongoDB.
- **Alternative**: "vince l'ultimo" (perde la prima modifica in silenzio, vietato dalla spec); header `If-Match`/ETag (più corretto per HTTP, ma più lavoro per il frontend; si può aggiungere dopo senza rompere il contratto).

## R5. Codice leggibile dell'evento

- **Decisione**: formato `<SIGLA>-<ANNO>-<NNN>` (es. `PIE-2027-001`), con `NAZ` per gli eventi nazionali e l'anno della data di partenza. Il progressivo viene da una collezione `counters` con `findAndModify` atomico (`$inc`, upsert) sulla chiave `PIE-2027`. Indice univoco su `code`.
- **Motivazione**: leggibile al telefono con la segreteria, univoco anche con richieste concorrenti; un documento per contatore, nessuna transazione.
- **Alternative**: UUID come codice (illeggibile); conteggio degli eventi esistenti + 1 (duplicati sotto carico).

## R6. Ricerca pubblica

- **Decisione**: `MongoTemplate` con `Criteria` costruiti dai filtri (`region`, `category`, `type`, `availability`, `startFrom`, `startTo`, `q` sul titolo con regex case-insensitive e testo escapato) più `Pageable` (`page`, `size` max 100, `sort=type|startDate|title,asc|desc`). Risposta: `content`, `page`, `size`, `totalElements`, `totalPages` dentro `payload`. Indici composti su `lifecycle + startsAt`, `region + startsAt`, `typeCode + startsAt`.
- **Motivazione**: FR-019 e SC-004 (1.000 eventi, prima pagina < 1 s): con gli indici una query è ampiamente nei tempi. La forma della pagina segue la convenzione `page`/`size`/`sort` di AGENTS.md.
- **Alternative**: metodi derivati del repository (troppe combinazioni di filtri); indice testuale MongoDB (non serve per una ricerca "contiene" sul solo titolo).

## R7. Errori di validazione con l'elenco dei campi

- **Decisione**: per 422 l'envelope porta in `payload` un elenco `[{ "field": "...", "message": "..." }]`. Vale sia per la validazione del corpo (Bean Validation) sia per gli invarianti della pubblicazione (FR-007), che vengono raccolti tutti prima di rispondere. Nuovi codici: `42201` invarianti di pubblicazione non rispettati; `40901` transizione di stato non consentita; `40902` codice tipo evento duplicato; `40903` versione superata; `40904` campo non modificabile dopo la pubblicazione. Dominio `CATALOG` per i codici di questa feature.
- **Motivazione**: FR-007 e US1 scenario 3 chiedono l'elenco di tutti i campi da correggere; l'envelope resta lo stesso (nessuna rottura del contratto, solo `payload` valorizzato anche in errore).
- **Alternative**: un errore alla volta (più giri per la segreteria, SC-001); `ProblemDetail` (escluso dalla spec).

## R8. Tipi evento iniziali

- **Decisione**: nel profilo `mocked` un `ApplicationRunner` del package `mock` crea, se mancano, tre tipi: `CFM_EG` (Campo di formazione metodologica E/G, categoria LEADER_TRAINING), `ROSS` (Route di orientamento al servizio, categoria RS), `CFA` (Campo di formazione associativa, categoria LEADER_TRAINING), con requisiti e adempimenti predefiniti plausibili. Fuori dal mock li crea l'amministrazione con le API.
- **Motivazione**: assunzione della spec ("catalogo iniziale caricato con i dati di prova del profilo mock"); serve per provare subito dal browser.
- **Alternative**: migrazione dati sempre attiva (mescolerebbe dati di prova e reali).

## R9. Visibilità delle bozze e dettaglio con token facoltativo

- **Decisione**: `GET /api/v1/events` e `GET /api/v1/events/{id}` sono pubblici in `SecurityConfig`; se arriva un token valido, il dettaglio di una BOZZA è visibile a chi la può gestire (R2), altrimenti 404. Un token presente ma non valido dà 401 anche sulle risorse pubbliche (comportamento standard del resource server). L'elenco "i miei eventi" in tutti gli stati è `GET /api/v1/events/managed`, protetto da `evento:gestione`: l'amministrazione vede tutto, la segreteria la propria regione.
- **Motivazione**: FR-009/FR-020 senza duplicare il dettaglio in due endpoint.
- **Alternative**: endpoint separato per il dettaglio delle bozze (duplica il DTO).

## R10. Date nel contratto

- **Decisione**: nel JSON le date di partenza, rientro, apertura e chiusura sono **date senza ora** (`2027-07-20`), come le indica la segreteria; gli istanti calcolati (R1) restano interni. `createdAt`/`updatedAt` sono istanti UTC ISO 8601. Quota in euro come numero decimale con due cifre (`BigDecimal`, salvato come `Decimal128`).
- **Motivazione**: BR-03 ragiona per giorni nell'ora italiana; il frontend non deve fare conti di fuso.
- **Alternative**: istanti nel contratto (il frontend dovrebbe sapere delle 09:00 e del fuso).

## R11. Requisiti, adempimenti e modalità di iscrizione

- **Decisione**: sotto-documenti dell'evento (e del tipo evento come default): `requirements { minAge, maxAge, branches[], memberTypes[], oneActivePerType, maxPerGroup }`, `obligations[] { type, description }` con `type` fra `LEADER_VALIDATION`, `UNIT_LEADER_AUTHORIZATION`, `GROUP_LEADER_AUTHORIZATION`, `PAYMENT`, `DOCUMENT`, `ELECTRONIC_FORM`; `enrollmentMode` fra `SELF`, `BY_LEADER`, `BOTH`. In questa feature sono solo salvati e mostrati (FR-006). `Branch` e `MemberType` riusano gli enum della feature 001.
- **Motivazione**: aggregato unico come nella spec (§2.3.3, "parti di un aggregato come sotto-documenti"); i nomi inglesi seguono la regola della lingua nel codice.
- **Alternative**: collezioni separate (inutili senza iscrizioni).
