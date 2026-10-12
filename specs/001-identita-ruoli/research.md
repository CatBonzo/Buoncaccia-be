# Research: Identità, ruoli e autenticazione mock

**Feature**: `001-identita-ruoli` | **Data**: 2026-10-12 | **Plan**: [plan.md](plan.md)

Ogni voce: decisione, motivazione, alternative scartate. Nessun punto resta aperto.

## R1. Validazione dei token: resource server di Spring Security

- **Decisione**: l'applicazione è un OAuth2 resource server (`spring-boot-starter-oauth2-resource-server`). Nel profilo `mocked` il `JwtDecoder` usa direttamente la chiave pubblica generata all'avvio (la stessa esposta su `/api/v1/mock/auth/jwks`) e verifica firma RS256, `iss = mock-auth` e scadenza. Fuori dal profilo `mocked` il decoder viene dalla configurazione standard (`spring.security.oauth2.resourceserver.jwt.issuer-uri` o `jwk-set-uri`), che in fase 6 punterà al realm Keycloak.
- **Motivazione**: è il meccanismo standard; il codice di autorizzazione non sa se il token è mock (FR-017, SC-006). Usare la chiave in memoria evita che l'applicazione chiami sé stessa via HTTP all'avvio o nei test con porta casuale.
- **Alternative**: `jwk-set-uri` del mock puntato su `localhost:8080` come dice la spec di progetto (funziona, ma lega la configurazione alla porta e complica i test); filtro di autenticazione scritto a mano (più codice, nessun vantaggio).

## R2. Generazione di chiave e token mock

- **Decisione**: all'avvio nel profilo `mocked` si genera una coppia RSA 2048 bit (Nimbus `RSAKeyGenerator`, già nel classpath con il resource server) e un token per utente con `NimbusJwtEncoder`. Chiave e token stanno solo in memoria in un bean singleton. Scadenza configurabile con `buonacaccia.mock-auth.token-validity` (default `P365D`, cioè "tutta la vita del processo" per l'uso locale).
- **Motivazione**: FR-003; al riavvio la chiave cambia e i vecchi token non passano più la verifica della firma (US1 scenario 3) senza dover tenere una lista di token revocati.
- **Alternative**: chiave fissa in un file (i token sopravvivrebbero al riavvio, contro la spec); HS256 con segreto condiviso (Keycloak usa RS256, la forma del token cambierebbe).

## R3. Claim del token

- **Decisione**: `iss`, `sub` (= identificativo Buona Caccia), `iat`, `exp`, `agesciUUID`, `isMemberOf`, `role` (lista), `entitlement` (lista), più i claim OIDC standard `preferred_username`, `email`, `given_name`, `family_name`.
- **Motivazione**: FR-006 chiede "almeno" i claim della spec; quelli OIDC li emette Keycloak per default e servono a creare l'utente al primo accesso (FR-011) senza un'altra fonte di dati. `role` è una lista anche se oggi ogni utente ne ha uno, per essere uguale a Keycloak (multivalore).
- **Alternative**: `role` come stringa singola (come nell'esempio YAML della spec): rischia di rompere i client quando arriveranno più ruoli.

## R4. Guardia `@SecurityAccess` su ruoli ed entitlement (rivista il 12/10)

- **Decisione**: annotazione `@SecurityAccess` sui metodi dei controller, sul modello della guardia del progetto ScoutShopper-be di Mahima, adattata ai claim di Buona Caccia: `@SecurityAccess(entitlement = Entitlements.EVENTO_GESTIONE)` richiede che il token contenga almeno uno degli entitlement indicati, `@SecurityAccess(role = Roles.AMMINISTRATORE)` almeno uno dei ruoli; senza attributi basta un token valido. La controlla un `HandlerInterceptor` (`SecurityGuardInterceptor`, package `config.guard`) prima di eseguire il metodo; se manca il permesso lancia `AccessDeniedException`, che diventa 403 con l'envelope. Le costanti stanno in `Roles` ed `Entitlements`. Regola d'uso: le operazioni si proteggono con l'entitlement (AUTH-1); il ruolo si usa solo per aree visibili a un ruolo.
- **Motivazione**: richiesta di Mahima del 12/10 ("la gestione delle guardie con i ruoli", come in ScoutShopper-be); un'annotazione dichiarativa per endpoint è leggibile come `@PreAuthorize` ma usa i nomi del dominio. L'interceptor non richiede AOP e legge i claim direttamente dal token già validato da Spring Security, quindi non ripete la validazione come faceva l'aspetto di ScoutShopper.
- **Alternative**: `@PreAuthorize("hasAuthority(...)")` (scelta del plan dell'12/10 mattina, sostituita); aspetto AOP come in ScoutShopper (serve `spring-boot-starter-aspectj` e un proxy per ogni controller, nessun vantaggio rispetto all'interceptor); livelli fissi `ADMIN/USER` (non bastano per gli entitlement).

## R11. Documentazione OpenAPI generata con springdoc e Swagger UI

- **Decisione**: `springdoc-openapi-starter-webmvc-ui` 3.0.3 (linea per Spring Boot 4.0), configurato in `application.yml` come in ScoutShopper-be: `/v3/api-docs` e `/swagger-ui.html`, entrambi pubblici. Uno schema di sicurezza `bearerAuth` permette di provare gli endpoint con il token preso da `findAll` (pulsante "Authorize").
- **Motivazione**: richiesta di Mahima; il frontend e chi sviluppa vedono e provano le API dal browser. Il contratto scritto a mano in `contracts/` resta la fonte di verità (costituzione, principio III); la documentazione generata serve a confrontarlo con il codice.
- **Alternative**: springdoc 3.1.x (compilato per Spring Boot 4.1); solo il file `contracts/` servito come statico (non mostra lo stato reale del codice).

## R12. File `test.env`

- **Decisione**: `test.env` attiva i profili `mocked,test`: utenti e token di prova come in `mocked.env`, ma database separato `buonacaccia-test` e log più dettagliati (`application-test.yml`).
- **Motivazione**: con il solo profilo `test` l'applicazione non avrebbe un emittente di token (Keycloak arriva in fase 6) e non partirebbe; così il file serve davvero a provare a mano senza toccare i dati di sviluppo.
- **Alternative**: lasciare `test` da solo (non avviabile); duplicare la configurazione mock nel profilo `test`.

## R5. Envelope e catalogo degli errori

- **Decisione**: classe Lombok `ApiResponse<T>` con i campi `int errorCode, String errorMessage, String errorDomain, T payload` (era un `record`, cambiato il 2026-10-12 su richiesta di Mahima). Codici a cinque cifre `SSSNN`, dove `SSS` è lo status HTTP e `NN` il dettaglio: `0` successo, `40100` non autenticato, `40300` permesso mancante, `40400` risorsa inesistente, `40900` conflitto, `42200` input non valido, `50000` errore interno. Domini: `AUTH`, `IDENTITY`, `SYSTEM`. Messaggi in italiano. Un `@RestControllerAdvice` gestisce le eccezioni dei controller; un `AuthenticationEntryPoint` e un `AccessDeniedHandler` scrivono lo stesso envelope per gli errori del filtro di sicurezza.
- **Motivazione**: FR-013…FR-015 e Q14 (status significativo). Il prefisso uguale allo status rende il catalogo leggibile ed estendibile (es. `42201` per un campo specifico) senza tabelle di conversione.
- **Alternative**: RFC 9457 Problem Details (scartato dalla spec v1.3 in favore dell'envelope); codici sequenziali `1, 2, 3` (non dicono nulla da soli).

## R6. Utenti di prova: file e caricamento

- **Decisione**: `mocked-identity.yml` viene importato solo nel profilo `mocked` (`spring.config.import: classpath:mocked-identity.yml` in `application-mocked.yml`, non opzionale) e legato a `@ConfigurationProperties("mocked-identity")` con validazione (`@NotEmpty`, `@NotBlank`). Un `ApplicationRunner` nel package `mockmigration` fa l'upsert di ogni voce su `agesciUUID` e fallisce se due voci hanno lo stesso `agesciUUID`. Radice del file: `mocked-identity.users` (la spec usa `mock-auth.users`; si allinea al nome scelto da Mahima).
- **Motivazione**: gli errori di file mancante, vuoto o malformato fermano l'avvio con il messaggio di Spring Boot che indica file e proprietà (casi limite della spec) senza codice di parsing nostro. `mockmigration` è il package previsto da AGENTS.md per i dati di prova.
- **Aggiornamento 12/10**: su richiesta di Mahima tutto il mock (questo file, `application-mocked.yml`, emittente dei token, `findAll`, `jwks`, caricamento degli utenti) è passato in `src/test`, package `it.buonacaccia.mock`, come in ScoutShopper-be; il package `mockmigration` resta per i dati di prova futuri. L'app con i mock si avvia con `TestBuonaCacciaApplication` (`./gradlew bootTestRun`).
- **Alternative**: lettura del file con un parser YAML a mano (più codice, messaggi peggiori); `optional:` nell'import (il backend partirebbe senza utenti, contro i casi limite).

## R7. Sincronizzazione di ruoli ed entitlement sull'utente (FR-011)

- **Decisione**: la copia di ruoli ed entitlement sull'utente si aggiorna in `UserService.syncFromToken`, chiamato da `GET /api/v1/users/me`. Se l'utente non esiste (cercato per `agesciUUID`) viene creato dai claim del token.
- **Motivazione**: `users/me` è la prima chiamata del frontend dopo il login, quindi coincide con "l'accesso"; aggiornare il database a ogni richiesta autenticata costerebbe una scrittura per chiamata senza benefici, perché le autorizzazioni usano sempre il token (AUTH-1).
- **Alternative**: filtro su ogni richiesta (scritture inutili); listener sull'evento di autenticazione di Spring (scatta comunque a ogni richiesta con token Bearer).

## R8. Persistenza: MongoDB con Spring Data

- **Decisione**: `spring-boot-starter-data-mongodb`; collezione `users`, `_id` = identificativo Buona Caccia (stringa, UUID generato se la voce non lo indica), indice univoco su `agesciUUID`, indice univoco sparse su `census.memberCode`. Indici creati dall'applicazione all'avvio (`auto-index-creation: true`).
- **Motivazione**: modello dati della spec di progetto (§2.4, Utente con censimento come sotto-documento). Indici dichiarati sul documento: uno solo punto da leggere.
- **Alternative**: script di inizializzazione del database in compose (la definizione si sdoppierebbe tra codice e infrastruttura).

## R9. Test: Testcontainers per MongoDB

- **Decisione**: test di integrazione con `@SpringBootTest` e un MongoDB in Testcontainers (`@ServiceConnection`), profilo `mocked` attivo. Un test separato avvia il contesto senza `mocked` per verificare che `/api/v1/mock/**` non esista. L'operazione di prova di US3 è un controller definito solo nei test.
- **Motivazione**: costituzione, principio II: il test usa un vero MongoDB (indici univoci, upsert) senza dipendere dal compose acceso. I runner di GitHub Actions hanno Docker, quindi la CI resta `./gradlew build`.
- **Alternative**: MongoDB embedded (Flapdoodle, non più consigliato e lontano dalla versione reale); mock del repository (non verifica indici e query).

## R10. Nomi nel codice e nel JSON

- **Decisione**: campi in inglese secondo il glossario della spec: `buonaCacciaUUID`, `agesciUUID`, `firstName`, `lastName`, `census.memberCode` (codice socio), `groupId`, `unitId`, `branch`, `memberType` (`YOUTH`/`LEADER`), `status` (`IN_GOOD_STANDING`, `NOT_IN_GOOD_STANDING`), `year`. I nomi italiani del modello dati della spec (`codiceSocio`, `statoCensimento`…) vanno allineati.
- **Motivazione**: regola di AGENTS.md (nomi nel codice in inglese); il JSON delle API riprende i nomi del codice.
- **Alternative**: nomi italiani nel JSON (incoerenti con il resto delle API, già in inglese: `/events`, `/enrollments`).

## R13. Lombok, log e circuit breaker (12/10, da ScoutShopper-be)

- **Decisione**: Lombok per i log (`@Slf4j`) e i costruttori (`@RequiredArgsConstructor`); log su console e file `logs/buonacaccia.log`; circuit breaker Resilience4j (istanza `mongodb`) attorno a ogni chiamata del repository, tramite `CircuitBreakerService.execute`. Guasto del database o circuito aperto → `BuonaCacciaException(SERVICE_UNAVAILABLE)` → 503 con l'envelope; `DuplicateKeyException` → 409 e non conta come guasto.
- **Motivazione**: richiesta di Mahima; sono le librerie che usava in ScoutShopper-be. Il circuit breaker evita che, con MongoDB giù, ogni richiesta aspetti il timeout del driver.
- **Alternative**: il `wrapCatch` di ScoutShopper con valore di fallback (catturando l'eccezione dentro il circuito, Resilience4j registrava sempre un successo e il circuito non si apriva; il chiamante riceveva `null`); annotazione `@CircuitBreaker` (serve AOP, `spring-boot-starter-aspectj`, e il metodo deve passare da un proxy); `@Log4j2` (Spring Boot usa Logback, `@Slf4j` è il collegamento diretto).
