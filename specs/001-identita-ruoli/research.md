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

## R4. Entitlement come authority e controllo per operazione

- **Decisione**: un `JwtAuthenticationConverter` trasforma ogni valore del claim `entitlement` in una `GrantedAuthority` con lo stesso nome, senza prefisso. Ogni operazione protetta dichiara l'entitlement con `@PreAuthorize("hasAuthority(...)")` usando le costanti della classe `Entitlements`. I ruoli non diventano authority: servono solo al frontend (visibilità).
- **Motivazione**: AUTH-1 e FR-008/FR-009 in una riga per endpoint, leggibile e verificabile nei test; nessun catalogo di permessi nel backend.
- **Alternative**: annotazione personalizzata `@RequiresEntitlement` (più codice per lo stesso effetto, da rivalutare se le regole relazionali di AUTH-2 la renderanno utile); controllo nel service a mano (sparso e facile da dimenticare).

## R5. Envelope e catalogo degli errori

- **Decisione**: record `ApiResponse<T>(int errorCode, String errorMessage, String errorDomain, T payload)`. Codici a cinque cifre `SSSNN`, dove `SSS` è lo status HTTP e `NN` il dettaglio: `0` successo, `40100` non autenticato, `40300` permesso mancante, `40400` risorsa inesistente, `40900` conflitto, `42200` input non valido, `50000` errore interno. Domini: `AUTH`, `IDENTITY`, `SYSTEM`. Messaggi in italiano. Un `@RestControllerAdvice` gestisce le eccezioni dei controller; un `AuthenticationEntryPoint` e un `AccessDeniedHandler` scrivono lo stesso envelope per gli errori del filtro di sicurezza.
- **Motivazione**: FR-013…FR-015 e Q14 (status significativo). Il prefisso uguale allo status rende il catalogo leggibile ed estendibile (es. `42201` per un campo specifico) senza tabelle di conversione.
- **Alternative**: RFC 9457 Problem Details (scartato dalla spec v1.3 in favore dell'envelope); codici sequenziali `1, 2, 3` (non dicono nulla da soli).

## R6. Utenti di prova: file e caricamento

- **Decisione**: `mocked-identity.yml` viene importato solo nel profilo `mocked` (`spring.config.import: classpath:mocked-identity.yml` in `application-mocked.yml`, non opzionale) e legato a `@ConfigurationProperties("mocked-identity")` con validazione (`@NotEmpty`, `@NotBlank`). Un `ApplicationRunner` nel package `mockmigration` fa l'upsert di ogni voce su `agesciUUID` e fallisce se due voci hanno lo stesso `agesciUUID`. Radice del file: `mocked-identity.users` (la spec usa `mock-auth.users`; si allinea al nome scelto da Mahima).
- **Motivazione**: gli errori di file mancante, vuoto o malformato fermano l'avvio con il messaggio di Spring Boot che indica file e proprietà (casi limite della spec) senza codice di parsing nostro. `mockmigration` è il package previsto da AGENTS.md per i dati di prova.
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
