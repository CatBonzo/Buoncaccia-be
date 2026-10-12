# AGENTS.md – Buona Caccia backend

Guida per chi lavora su questo repository, persone o agenti IA. Va aggiornata a ogni passo del progetto: ogni scelta nuova entra nel registro della sezione 4, con data e motivazione, nello stesso commit che la applica.

## 1. Contesto del progetto

- **Cosa è.** Backend per la gestione delle iscrizioni agli eventi AGESCI (nazionali, regionali, di zona), pensato come evoluzione della piattaforma [BuonaCaccia](https://buonacaccia.agesci.it/). È il progetto di tesi di Mahima Pichini.
- **Metodo.** Spec-driven development con Spec-Kit: la specifica è la fonte di verità, il codice si scrive solo dopo `specify`, `clarify`, `plan` e `tasks`. Specifica di riferimento: *Buona Caccia – Note Tecniche Backend* v2.0 (10/10/2026), con in fondo la checklist "cosa manca da fare".
- **Piano di lavoro.** Planner v2 (PC e Docker), fasi 0–6 dell'MVP: 0 fondamenta, 1 identità e auth mock, 2 catalogo eventi, 3 iscrizioni e lista d'attesa, 4 eventi di dominio e notifiche, 5 bonifico registrato dalla segreteria, 6 passaggio a Keycloak.
- **Perimetro dell'MVP.** Tre ruoli: amministrazione, segreteria, user. Il resto della spec (capo presentatore, staff, pagamenti elettronici, documenti, candidature) arriva dopo.
- **Ambiente.** Solo uso locale, nessuna pubblicazione. Tutto gira con Docker Compose sul PC di sviluppo, in un progetto Compose separato (`buonacaccia`) per tenere la tesi distinta dal lavoro.
- **Stato attuale.** Fase 0 chiusa: applicazione Spring Boot (Gradle) sulla porta 8080 con `/actuator/health`; `compose.yaml` con MongoDB, Kafka e Mailpit; file di ambiente in `test-src/env/`; Spec-Kit inizializzato con la costituzione; CI su GitHub Actions. Fase 1 (identità e auth mock) in corso sul branch `001-identita-ruoli`: spec scritta in `specs/001-identita-ruoli/spec.md`, Q14 e Q15 chiuse; plan, tasks e implementazione fatti: token mock, guardia `@SecurityAccess`, envelope con status significativo, `/users/me`, Swagger UI; 31 test verdi. Prossimo passo: revisione e pull request verso `feature`.
- **Architettura.** Un solo progetto Spring Boot, non più cinque microservizi: le aree funzionali (identità, eventi, iscrizioni, notifiche) convivono nella stessa applicazione e si distinguono nei package `service`, `v1` e `model`.

## 2. Coordinamento con il frontend

Il frontend è un progetto separato che consuma le API; questo repository non contiene interfacce grafiche. Regole del contratto:

- **Un solo indirizzo.** Il frontend chiama `http://localhost:8080/api/v1/...`.
- **Contratto prima del codice.** Ogni API è descritta in OpenAPI 3.1 nella cartella della feature (`specs/NNN-*/contracts/`) prima di essere implementata. Il frontend lavora su quel file, anche prima che l'endpoint esista.
- **Envelope unico.** Ogni risposta, di successo o di errore, ha la forma `{ "errorCode", "errorMessage", "errorDomain", "payload" }`; `errorCode` = 0 significa successo e i dati sono in `payload`.
- **Status HTTP.** Significativo (401, 403, 404, 409, 422, 500) e coerente con `errorCode`; il corpo è sempre l'envelope (Q14, 12/10).
- **Documentazione viva.** Swagger UI su `http://localhost:8080/swagger-ui.html` (JSON su `/v3/api-docs`): "Authorize" accetta un `accessToken` preso da `findAll`.
- **Autenticazione, fase 1.** Profilo Spring `mocked`, utenti di prova in `src/test/resources/mocked-identity.yml`; tutto il mock sta nel codice di test (package `it.buonacaccia.mock`) e si avvia con `TestBuonaCacciaApplication`. `GET /api/v1/mock/auth/findAll` restituisce gli utenti di prova con il loro `accessToken`; il frontend lo manda come `Authorization: Bearer <token>`. I token cambiano a ogni riavvio del backend.
- **Autenticazione, fase 2.** Keycloak con OIDC (authorization code + PKCE), realm `buonacaccia`. I claim del token restano gli stessi, quindi il frontend non cambia logica.
- **Ruoli ed entitlement.** Il claim `role` dice che cosa mostrare (menu, aree); il claim `entitlement` dice quali azioni abilitare. Il frontend può nascondere un pulsante, ma il backend controlla sempre l'entitlement.
- **Convenzioni.** Paginazione `page`/`size`, ordinamento `sort=campo,asc|desc`, date ISO 8601 in UTC (le regole di dominio usano Europe/Rome), risorse al plurale in inglese.
- **Azioni disponibili.** La risposta di un'iscrizione elenca le azioni possibili e gli adempimenti mancanti (NFR-12), così il frontend non ricalcola le regole.
- **Compatibilità.** Una modifica incompatibile va solo in una nuova versione (`/api/v2`). Ogni PR dichiara l'impatto sulle API.
- **Da definire.** Repository e tecnologia del frontend, origine da abilitare in CORS per lo sviluppo.

## 3. Regole di lavoro

- **Branch.** `feature` è il branch principale di lavoro; il merge verso `main` si farà quando il risultato convince. Le feature Spec-Kit nascono su un branch `NNN-titolo-breve` creato da `feature` e tornano su `feature` con una pull request.
- **Commit.** Formato `verbo: sintesi` (`add`, `fix`, `refactor`, `docs`, `test`, `chore`), con il riferimento al requisito quando c'è (es. `add: lista d'attesa (FR-036)`). Identità Git personale, mai quella di lavoro.
- **Lingua.** Specifiche, documentazione e commenti in italiano; nomi nel codice (classi, metodi, campi, endpoint) in inglese, come nel glossario della spec.
- **Test.** I test definiscono il "fatto": scritti prima, devono fallire e poi passare. `./gradlew build` deve passare prima di ogni push.
- **Versioni.** Tutte in `gradle/libs.versions.toml`; dipendenze e plugin in `build.gradle` alla radice. Immagini Docker con tag fissato, mai `latest`.
- **Segreti.** Mai nel repository: `.env` è ignorato. I file in `test-src/env/` contengono solo valori locali di sviluppo, nessuna password reale.
- **Ambiente locale.** `docker compose up -d` avvia MongoDB (replica set `rs0`, porta 27017), Kafka (KRaft, porta 9092) e Mailpit (SMTP 1025, web 8025), tutti su `127.0.0.1`. L'applicazione con i mock si avvia da IntelliJ lanciando `TestBuonaCacciaApplication` (in `src/test`) con le variabili di `test-src/env/mocked.env`, oppure con `./gradlew bootTestRun`.
- **Spec-Kit.** Comandi come skill di Claude Code in `.claude/skills/speckit-*`; costituzione in `.specify/memory/constitution.md`; le feature vanno in `specs/NNN-titolo-breve/`.
- **Struttura del progetto.** Standard Spring Boot: codice in `src/main/java`, risorse in `src/main/resources`, test in `src/test/java`. Package radice `it.buonacaccia` con la classe `BuonaCacciaApplication` e questi package:

  | Package | Contenuto |
  | --- | --- |
  | `config` | Configurazione Spring (sicurezza, OpenAPI, web); `config.error` catalogo errori, eccezione applicativa e gestore globale; `config.mongo` circuit breaker su MongoDB |
  | `guard` | Guardia degli endpoint: annotazione `@SecurityAccess` e interceptor |
  | `model` | Documenti MongoDB, value object, stati, costanti di ruoli ed entitlement |
  | `repository` | Repository Spring Data MongoDB |
  | `service` | Logica di business e regole BR della spec; `service.guard` regole di autorizzazione della guardia |
  | `v1` | API versione 1: `v1.controller` (controller REST, nessuna logica), `v1.dto` (DTO ed envelope), `v1.mapper` (dominio ↔ DTO) |

  Nei test (`src/test/java`) gli stessi package, più `mock` (auth mock e utenti di prova), `AbstractTest` e `TestBuonaCacciaApplication`.

  Le dipendenze vanno in una sola direzione: `v1` → `service` → `repository` → `model`.
- **Questo file.** A ogni passo si aggiornano lo stato (sezione 1) e il registro delle scelte (sezione 4).
- **Diario della tesi.** A ogni passo si aggiunge una voce in `docs/tesi/diario-capitolo-3.txt` (testo semplice): che cosa si è fatto, chi ha deciso e chi ha eseguito, quale paradigma del capitolo 2 si vede, artefatti, problemi incontrati. Serve a documentare il capitolo 3.

## 4. Scelte prese e perché

| Data | Scelta | Perché |
| --- | --- | --- |
| 2026-10-07 | Database NoSQL (MongoDB) | Decisione della spec: aggregati come documenti, censimento come sotto-documento dell'utente |
| 2026-10-07 | Notifiche con Apache Kafka (ADR-003) | Disaccoppia i servizi; nuovi canali come nuovi consumer |
| 2026-10-07 | Iscrizioni senza vincoli territoriali (BR-15) | Un socio può iscriversi a eventi di qualunque regione |
| 2026-10-09 | Entitlement gestiti solo nell'Identity Provider | Il backend non ha un catalogo di permessi suo: legge i claim del token |
| 2026-10-09 | Auth in due fasi: token mock, poi Keycloak | Si sviluppa subito senza infrastruttura; il passaggio è solo di configurazione |
| 2026-10-09 | Envelope `errorCode/errorMessage/errorDomain/payload` | Stessa forma per ogni risposta, più semplice per il frontend |
| 2026-10-10 | Docker Compose sul PC, non più Raspberry | Nessun limite di memoria o di architettura; immagini ufficiali; tesi isolata dal lavoro |
| 2026-10-10 | MVP con tre ruoli (amministrazione, segreteria, user) | Solo l'essenziale; gli altri ruoli dopo l'MVP |
| 2026-10-10 | Valori dei ruoli `agesci:sistema:amministratore`, `agesci:regione:segreteria`, `agesci:socio:user` e degli entitlement dell'MVP (confermati da Mahima l'11/10, Q15) | Seguono lo schema `agesci:<livello>:<funzione>` della spec |
| 2026-10-10 | Cinque microservizi per l'MVP (superata l'11/10) | La tesi è a microservizi; si parte dal numero minimo (costituzione, principio IV) |
| 2026-10-11 | Branch `feature` come principale, merge su `main` più avanti | Scelta di Mahima |
| 2026-10-11 | Un solo repository Gradle multi-modulo (superata in giornata) | Un solo progetto da aprire in IntelliJ, una sola build e CI |
| 2026-10-11 | Spring Boot 4.0.8 (la spec dice 3.x: da confermare) | Ultima versione stabile della 4.0; Spring Cloud non serve più senza gateway |
| 2026-10-11 | Gradle 9.8.1 con wrapper e catalogo delle versioni | Versioni in un solo punto; build ripetibile senza Gradle installato |
| 2026-10-11 | File di build in Groovy (`build.gradle`) | Lo stesso formato dei progetti Spring Boot che Mahima usa già |
| 2026-10-11 | Struttura a package `api`, `config`, `job`, `mapper`, `mockmigration`, `model`, `repository.nosql`, `service`, `utils` (superata il 12/10) | Scelta di Mahima, dalla struttura dei progetti Spring Boot che conosce |
| 2026-10-11 | Classe principale `BuonaCacciaApplication` | Convenzione Spring Boot `NomeAppApplication` |
| 2026-10-11 | Cartella `.idea` fuori dal repository | IntelliJ la ricrea importando Gradle; evita conflitti tra macchine |
| 2026-10-11 | Diario `docs/tesi/diario-capitolo-3.txt` aggiornato a ogni passo | Materiale per il capitolo 3 della tesi: collega ogni passo ai paradigmi del capitolo 2 |
| 2026-10-11 | Un'unica applicazione Spring Boot in `src/main/java`, senza moduli né gateway | Scelta di Mahima: la struttura standard di Spring Boot; più semplice da capire, avviare e documentare |
| 2026-10-11 | File degli utenti di prova `mocked-identity.yml`, profilo `mocked`, variabili in `test-src/env/mocked.env` e `test.env` | Nomi scelti da Mahima (la spec dice `mock-identity.yml` e profilo `mock-auth`: da allineare) |
| 2026-10-11 | `compose.yaml` con `mongo:8.0.32`, `apache/kafka:4.3.1`, `axllent/mailpit:v1.31.5`, tag fissati nel file | Versioni stabili più recenti; tag nel file versionato così l'ambiente è uguale per tutti |
| 2026-10-11 | MongoDB come replica set a un nodo | Le transazioni (outbox, posti) richiedono un replica set |
| 2026-10-11 | Spec-Kit con integrazione Claude Code (skill in `.claude/skills`), costituzione 1.0.0 dai 4 principi della spec | È il metodo della tesi; i comandi sono disponibili all'agente che sviluppa |
| 2026-10-11 | CI su GitHub Actions: `./gradlew build` a ogni push e PR su `feature` e `main` | Verifica automatica e ripetibile (sensore deterministico) |
| 2026-10-12 | Status HTTP significativo (401, 403, 404, 409, 422, 500) con l'envelope nel corpo (Q14, scelta di Mahima) | Usa la semantica standard di HTTP: client, proxy e strumenti capiscono l'esito senza leggere il corpo; `errorCode` resta per il dettaglio applicativo |
| 2026-10-12 | Resource server di Spring Security; nel profilo `mocked` il decoder usa la chiave RSA in memoria, fuori dal mock `issuer-uri`/`jwk-set-uri` di Keycloak | Meccanismo standard, passaggio a Keycloak solo di configurazione; evita che l'app chiami sé stessa via HTTP (research R1) |
| 2026-10-12 | Entitlement del token come authority con `@PreAuthorize` (superata in giornata dalla guardia `@SecurityAccess`) | AUTH-1 in una riga per endpoint (R4) |
| 2026-10-12 | Codici di errore `SSSNN` (status HTTP + dettaglio): 40100, 40300, 40400, 40900, 42200, 50000; domini `AUTH`, `IDENTITY`, `SYSTEM` | Leggibili ed estendibili senza tabelle di conversione; chiude il "catalogo da definire" della spec (R5) |
| 2026-10-12 | Radice del file utenti di prova `mocked-identity.users`, import non opzionale nel profilo `mocked` | Allineata al nome del file scelto da Mahima; file mancante o malformato ferma l'avvio (R6) |
| 2026-10-12 | Claim `role` come lista e claim OIDC standard (`preferred_username`, `email`, `given_name`, `family_name`) nel token mock | Stessa forma di Keycloak; servono a creare l'utente al primo accesso (R3) |
| 2026-10-12 | Ruoli ed entitlement copiati sull'utente alla chiamata di `/users/me` | È la prima chiamata del frontend dopo il login; niente scritture a ogni richiesta (R7) |
| 2026-10-12 | Campi in inglese nel codice e nel JSON (`memberCode`, `branch`, `memberType`…) | Regola della lingua nel codice; la spec usa nomi italiani da allineare (R10) |
| 2026-10-12 | Test di integrazione con Testcontainers (MongoDB) | Test su un database vero senza dipendere dal compose acceso; funziona anche in CI (R9) |
| 2026-10-12 | Guardia `@SecurityAccess(role = …, entitlement = …)` controllata da un `HandlerInterceptor` (package `config.guard`) | Richiesta di Mahima, sul modello di ScoutShopper-be; legge i claim del token già validato, niente AOP (R4) |
| 2026-10-12 | Swagger UI con springdoc 3.0.3, configurato in `application.yml` (`/v3/api-docs`, `/swagger-ui.html`, pubblici) e schema `bearerAuth` | Richiesta di Mahima, come in ScoutShopper-be; il contratto in `contracts/` resta la fonte di verità (R11) |
| 2026-10-12 | `test.env` con profili `mocked,test` e database `buonacaccia-test` | Con il solo profilo `test` l'app non avrebbe un emittente di token (R12) |
| 2026-10-12 | `mocked-identity.yml` sul modello di `identity.yaml` di ScoutShopper-be, con i campi della spec (claim, censimento) | Richiesta di Mahima; la spec chiede `agesciUUID`, entitlement e censimento oltre ai ruoli (R6) |
| 2026-10-12 | Codice di errore `40500` (metodo non supportato) | Senza, un metodo HTTP sbagliato diventava 500 |
| 2026-10-12 | Mock in `src/test` come in ScoutShopper-be: package `it.buonacaccia.mock` (emittente dei token, utenti di prova, `findAll`, `jwks`), `mocked-identity.yml` e `application-mocked.yml` in `src/test/resources`; `AbstractTest` crea il DB con Testcontainers; avvio locale con `TestBuonaCacciaApplication` / `bootTestRun` | Richiesta di Mahima; il jar dell'applicazione non contiene codice di prova né un emittente di token |
| 2026-10-12 | Package come in ScoutShopper-be: `config` (con `error`, `mongo`), `guard`, `model`, `repository`, `service` (con `guard`), `v1` (`controller`, `dto`, `mapper`); tolti `api`, `job`, `mapper`, `mockmigration`, `utils` | Scelta di Mahima, dallo screenshot della struttura di ScoutShopper; la versione dell'API (`v1`) è anche nel nome dei package |
| 2026-10-12 | Lombok (versione dal BOM di Spring Boot, `lombok.config` alla radice): `@Slf4j` per i log, `@RequiredArgsConstructor` per l'iniezione; ~~i DTO e i documenti restano `record`~~ (superata, vedi riga sotto sulle classi Lombok) | Richiesta di Mahima, come in ScoutShopper-be; meno codice ripetuto. `@Slf4j` al posto di `@Log4j2` perché Spring Boot usa Logback |
| 2026-10-12 | Log su console e su `logs/buonacaccia.log` (fuori da git), livelli in `application.yml`, DEBUG con il profilo `test` | Richiesta di Mahima, come in ScoutShopper-be |
| 2026-10-12 | Circuit breaker Resilience4j 2.4.0 (`resilience4j-spring-boot4`) sulle chiamate a MongoDB: `config.mongo.CircuitBreakerService`, istanza `mongodb` in `application.yml`; database giù o circuito aperto = 503 (`50300`), chiave duplicata = 409 | Richiesta di Mahima, dal `CircuitBreakerService` di ScoutShopper-be. Differenza voluta: niente fallback silenzioso (con il fallback il circuito non si apriva mai e gli errori sparivano) (R13) |
| 2026-10-12 | Documenti MongoDB (`User`, `Census`) e DTO (`ApiResponse`, `UserProfileResponse`, `CensusResponse`, `MockUserResponse`) sono classi Java con `@AllArgsConstructor @NoArgsConstructor @Getter @Setter @ToString @Builder` (più `@Document` sui documenti), non più `record`. Restano `record` solo le proprietà lette da `mocked-identity.yml` e gli aiuti interni dei test | Richiesta di Mahima: con le classi si lavora sul DB come in ScoutShopper-be, cioè si carica il documento, si cambiano i campi con i setter e si salva; il costruttore vuoto serve anche a Spring Data e Jackson. Supera la riga che teneva i `record` |
| 2026-10-12 | Mapper con MapStruct 1.6.3 (più `lombok-mapstruct-binding` 0.2.0 perché i due processori lavorino insieme): `v1.mapper.UserMapper` è un'interfaccia `@Mapper(unmappedTargetPolicy = IGNORE)` con `INSTANCE = Mappers.getMapper(...)`, metodi `toDto`, `toDtoList`, `toCensusDto`; l'implementazione è generata in compilazione. `@Mapping` solo dove i nomi differiscono (`id` → `buonaCacciaUUID`, `member` da `isMember()`) | Richiesta di Mahima, come lo `UserMapper` di ScoutShopper-be: niente codice di copia scritto a mano. Nessun `toModel` per ora: il profilo è solo in lettura |

## 5. Domande aperte

- Q11: una segreteria può gestire eventi di qualunque regione? (per l'MVP: sì)
- Q12: rimborso con ritiro tardivo, US-10 o BR-10? (non serve nell'MVP)
- Versione di Spring Boot: 4.0 al posto della 3.x della spec.
- Spec di progetto da allineare alla feature 001: nomi dei campi del censimento in inglese, radice `mocked-identity.users`, profilo `mocked`.
- Spec e planner descrivono ancora i microservizi (par. 2.8, Appendice B, Planner v2): vanno allineati all'applicazione unica.
- Kafka per le notifiche (ADR-003): resta utile dentro un'applicazione unica o si passa a eventi interni di Spring? (per ora resta in compose)
- A cosa serve `test-src/config/develop.json`? (per ora vuoto)
