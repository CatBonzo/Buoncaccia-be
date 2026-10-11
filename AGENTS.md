# AGENTS.md – Buona Caccia backend

Guida per chi lavora su questo repository, persone o agenti IA. Va aggiornata a ogni passo del progetto: ogni scelta nuova entra nel registro della sezione 4, con data e motivazione, nello stesso commit che la applica.

## 1. Contesto del progetto

- **Cosa è.** Backend per la gestione delle iscrizioni agli eventi AGESCI (nazionali, regionali, di zona), pensato come evoluzione della piattaforma [BuonaCaccia](https://buonacaccia.agesci.it/). È il progetto di tesi di Mahima Pichini.
- **Metodo.** Spec-driven development con Spec-Kit: la specifica è la fonte di verità, il codice si scrive solo dopo `specify`, `clarify`, `plan` e `tasks`. Specifica di riferimento: *Buona Caccia – Note Tecniche Backend* v2.0 (10/10/2026), con in fondo la checklist "cosa manca da fare".
- **Piano di lavoro.** Planner v2 (PC e Docker), fasi 0–6 dell'MVP: 0 fondamenta, 1 identità e auth mock, 2 catalogo eventi, 3 iscrizioni e lista d'attesa, 4 eventi di dominio e notifiche, 5 bonifico registrato dalla segreteria, 6 passaggio a Keycloak.
- **Perimetro dell'MVP.** Tre ruoli: amministrazione, segreteria, user. Il resto della spec (capo presentatore, staff, pagamenti elettronici, documenti, candidature) arriva dopo.
- **Ambiente.** Solo uso locale, nessuna pubblicazione. Tutto gira con Docker Compose sul PC di sviluppo, in un progetto Compose separato (`buonacaccia`) per tenere la tesi distinta dal lavoro.
- **Stato attuale.** Fase 0: scheletro Gradle con cinque servizi Spring Boot che si avviano e rispondono su `/actuator/health`. Mancano Docker Compose, Spec-Kit e CI.

### Servizi

| Modulo | Porta | Responsabilità |
| --- | --- | --- |
| `api-gateway` | 8080 | Unico punto d'ingresso, instrada `/api/v1/**` ai servizi |
| `identity-service` | 8081 | Utenti, `/users/me`, auth mock (`mock-identity.yml`) |
| `event-service` | 8082 | Tipi evento, eventi, catalogo pubblico, scheduler degli stati |
| `enrollment-service` | 8083 | Iscrizioni, posti, lista d'attesa, adempimenti, bonifico |
| `notification-service` | 8084 | Consumer Kafka, e-mail |

## 2. Coordinamento con il frontend

Il frontend è un progetto separato che consuma le API; questo repository non contiene interfacce grafiche. Regole del contratto:

- **Un solo indirizzo.** Il frontend chiama sempre il gateway: `http://localhost:8080/api/v1/...`. Non chiama mai i servizi sulle loro porte.
- **Contratto prima del codice.** Ogni API è descritta in OpenAPI 3.1 nella cartella della feature (`specs/NNN-*/contracts/`) prima di essere implementata. Il frontend lavora su quel file, anche prima che l'endpoint esista.
- **Envelope unico.** Ogni risposta, di successo o di errore, ha la forma `{ "errorCode", "errorMessage", "errorDomain", "payload" }`; `errorCode` = 0 significa successo e i dati sono in `payload`.
- **Status HTTP.** Default: significativo (401, 403, 404, 409, 422). Decisione aperta (Q14).
- **Autenticazione, fase 1.** `GET /api/v1/mock/auth/findAll` restituisce gli utenti di prova con il loro `accessToken`; il frontend lo manda come `Authorization: Bearer <token>`. I token cambiano a ogni riavvio del backend.
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
- **Versioni.** Tutte in `gradle/libs.versions.toml`; le impostazioni comuni dei servizi in `buildSrc/src/main/groovy/buonacaccia.spring-service.gradle`. Immagini Docker con tag fissato, mai `latest`.
- **Segreti.** Mai nel repository: `.env` è ignorato, nel repo va solo `.env.example`.
- **Struttura di un servizio.** Package radice `it.buonacaccia.<servizio>` con la classe `*Application` e questi package (ogni servizio ha solo quelli che gli servono):

  | Package | Contenuto |
  | --- | --- |
  | `api` | Controller REST e DTO; nessuna logica di business |
  | `config` | Configurazione Spring |
  | `job` | Attività pianificate (scheduler) |
  | `mapper` | Conversioni dominio ↔ DTO |
  | `mockmigration` | Dati di prova caricati all'avvio, solo nei profili di sviluppo |
  | `model` | Documenti MongoDB, value object, stati |
  | `repository.nosql` | Repository Spring Data MongoDB |
  | `service` | Logica di business e regole BR della spec |
  | `utils` | Utilità interne al servizio |

  Le dipendenze vanno in una sola direzione: `api` → `service` → `repository.nosql` → `model`. I servizi non leggono il database di un altro servizio: si parlano con eventi su Kafka.
- **Questo file.** A ogni passo si aggiornano lo stato (sezione 1) e il registro delle scelte (sezione 4).

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
| 2026-10-10 | Valori dei ruoli `agesci:sistema:amministratore`, `agesci:regione:segreteria`, `agesci:socio:user` (da confermare, Q15) | Seguono lo schema `agesci:<livello>:<funzione>` della spec |
| 2026-10-10 | Cinque microservizi per l'MVP | La tesi è a microservizi; si parte dal numero minimo (costituzione, principio IV) |
| 2026-10-11 | Branch `feature` come principale, merge su `main` più avanti | Scelta di Mahima |
| 2026-10-11 | Un solo repository Gradle multi-modulo | Un solo progetto da aprire in IntelliJ, una sola build e CI |
| 2026-10-11 | Spring Boot 4.0.8 e Spring Cloud 2025.1.3 (la spec dice 3.x: da confermare) | Ultima coppia stabile e compatibile; Boot 4.1 non ha ancora uno Spring Cloud stabile |
| 2026-10-11 | Gradle 9.8.1 con wrapper, catalogo delle versioni, plugin di convenzione in `buildSrc` | Versioni in un solo punto; i servizi dichiarano solo le loro dipendenze |
| 2026-10-11 | File di build in Groovy (`build.gradle`) | Lo stesso formato dei progetti Spring Boot che Mahima usa già |
| 2026-10-11 | Struttura a package `api`, `config`, `job`, `mapper`, `mockmigration`, `model`, `repository.nosql`, `service`, `utils` in ogni servizio | Scelta di Mahima, dalla struttura che conosce; tenuti i cinque servizi |
| 2026-10-11 | Classe principale `*Application` (es. `IdentityServiceApplication`) invece di `App` | Con cinque servizi nello stesso progetto, in IntelliJ si distinguono le configurazioni di avvio |
| 2026-10-11 | Cartella `.idea` fuori dal repository | IntelliJ la ricrea importando Gradle; evita conflitti tra macchine |

## 5. Domande aperte

- Q11: una segreteria può gestire eventi di qualunque regione? (per l'MVP: sì)
- Q12: rimborso con ritiro tardivo, US-10 o BR-10? (non serve nell'MVP)
- Q14: status HTTP significativo o sempre 200? (default: significativo)
- Q15: valori di `role` ed entitlement definitivi.
- Versione di Spring Boot: 4.0 al posto della 3.x della spec.
