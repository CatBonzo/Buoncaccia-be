# Implementation Plan: Identità, ruoli e autenticazione mock

**Branch**: `001-identita-ruoli` | **Date**: 2026-10-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-identita-ruoli/spec.md`

## Summary

Il backend diventa un OAuth2 resource server che accetta token JWT RS256. Nel profilo `mocked` genera all'avvio una chiave e un token per ciascun utente di `mocked-identity.yml`, li tiene in memoria e li espone su `GET /api/v1/mock/auth/findAll` (con la chiave pubblica su `/jwks`); gli utenti sono salvati in MongoDB con upsert su `agesciUUID`. `GET /api/v1/users/me` restituisce il profilo e allinea ruoli ed entitlement dal token. Gli entitlement del token diventano le authority di Spring Security e ogni operazione dichiara il suo con `@PreAuthorize`. Tutte le risposte, anche gli errori del filtro di sicurezza, usano l'envelope con status HTTP significativo. Dettagli in [research.md](research.md).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.0.8 (webmvc, actuator, security, oauth2-resource-server, data-mongodb, validation); Nimbus JOSE+JWT (tramite il resource server) per chiave e token mock

**Storage**: MongoDB 8.0 (replica set `rs0` in compose), collezione `users`

**Testing**: JUnit 5, Spring Boot Test, Spring Security Test, Testcontainers (MongoDB)

**Target Platform**: applicazione Spring Boot su PC di sviluppo (Docker Compose per l'infrastruttura), CI su GitHub Actions

**Project Type**: web service (REST API per un frontend separato)

**Performance Goals**: nessun obiettivo specifico; SC-001 (token in meno di un minuto dall'avvio)

**Constraints**: solo uso locale (127.0.0.1); nessun segreto nel repository; contratto `/api/v1` stabile; passaggio a Keycloak solo di configurazione (FR-017)

**Scale/Scope**: 4 utenti di prova, 3 endpoint, 1 collezione

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Verifica | Esito |
| --- | --- | --- |
| I. Qualità del codice | Controller in `api` senza logica; regole in `service`; repository in `repository.nosql`; DTO ↔ dominio in `mapper`; dati di prova in `mockmigration` | ✅ |
| II. Test-first | Test di integrazione per ogni user story e caso limite, scritti prima del codice; `./gradlew build` in CI con Testcontainers | ✅ |
| III. Coerenza con i client | Contratto OpenAPI 3.1 in `contracts/` prima del codice; envelope unico anche per 401/403/404; status significativo (Q14); nessuna modifica incompatibile | ✅ |
| IV. Semplicità | Meccanismi standard di Spring Security (resource server, authority, `@PreAuthorize`); nessun catalogo di permessi; nessuna nuova infrastruttura oltre MongoDB già in compose | ✅ |
| Vincoli tecnologici | Java 21, Boot 4.0, Gradle Groovy, versioni nel catalogo; profilo `mocked`; Kafka e Mailpit non servono a questa feature | ✅ |

Ricontrollo dopo la Fase 1 (design): ✅ nessuna violazione. Unica deviazione dalla spec di progetto, documentata in R1: nel profilo `mocked` il decoder usa la chiave in memoria invece del `jwk-set-uri` HTTP; l'endpoint `/jwks` resta disponibile e il contratto non cambia.

## Project Structure

### Documentation (this feature)

```text
specs/001-identita-ruoli/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── identity.openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
src/main/java/it/buonacaccia/
├── BuonaCacciaApplication.java
├── api/
│   ├── MockAuthController.java          # @Profile("mocked"): findAll, jwks
│   ├── UserController.java              # users/me
│   ├── GlobalExceptionHandler.java      # @RestControllerAdvice → envelope
│   └── dto/
│       ├── ApiResponse.java             # envelope
│       ├── MockUserResponse.java
│       └── UserProfileResponse.java
├── config/
│   ├── SecurityConfig.java              # filter chain, converter entitlement → authority, entry point e access denied con envelope
│   ├── MockAuthConfig.java              # @Profile("mocked"): chiave RSA, JwtEncoder, JwtDecoder
│   └── MockedIdentityProperties.java    # @ConfigurationProperties("mocked-identity")
├── mapper/
│   └── UserMapper.java
├── mockmigration/
│   └── MockedUsersLoader.java           # @Profile("mocked"): upsert utenti all'avvio
├── model/
│   ├── User.java, Census.java
│   ├── UserStatus.java, Branch.java, MemberType.java, CensusStatus.java
│   ├── Roles.java, Entitlements.java    # costanti dei valori confermati (Q15)
├── repository/nosql/
│   └── UserRepository.java
├── service/
│   ├── MockTokenService.java            # @Profile("mocked"): token in memoria
│   ├── UserService.java                 # profilo, syncFromToken
│   └── exception/BuonaCacciaException.java
└── utils/
    └── ErrorCode.java                   # catalogo codici, status, dominio, messaggio

src/main/resources/
├── application.yml                      # comune; resource server Keycloak per i profili non mock
├── application-mocked.yml               # import di mocked-identity.yml, validità token
└── mocked-identity.yml                  # 4 utenti di prova

src/test/java/it/buonacaccia/
├── BuonaCacciaApplicationTest.java      # health (esistente)
├── TestcontainersConfiguration.java     # MongoDB con @ServiceConnection
├── api/MockAuthControllerTest.java      # US1
├── api/UserControllerTest.java          # US2
├── api/EntitlementPolicyTest.java       # US3, con controller di prova solo nei test
├── api/EnvelopeTest.java                # US4
├── api/MockProfileDisabledTest.java     # US1 scenario 4
└── mockmigration/MockedUsersLoaderTest.java  # casi limite del file
```

**Structure Decision**: applicazione Spring Boot unica con i package di AGENTS.md; dipendenze `api` → `service` → `repository.nosql` → `model`, con `config` che collega i pezzi e `utils` condiviso. Le classi del profilo `mocked` sono marcate `@Profile("mocked")` e spariscono in fase 6.

## Complexity Tracking

Nessuna violazione della costituzione da giustificare.
