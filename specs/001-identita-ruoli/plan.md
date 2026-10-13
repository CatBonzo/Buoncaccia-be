# Implementation Plan: Identità, ruoli e autenticazione mock

**Branch**: `001-identita-ruoli` | **Date**: 2026-10-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-identita-ruoli/spec.md`

## Summary

Il backend diventa un OAuth2 resource server che accetta token JWT RS256. Nel profilo `mocked` genera all'avvio una chiave e un token per ciascun utente di `mocked-identity.yml`, li tiene in memoria e li espone su `GET /api/v1/mock/auth/findAll` (con la chiave pubblica su `/jwks`); gli utenti sono salvati in MongoDB con upsert su `agesciUUID`. `GET /api/v1/users/me` restituisce il profilo e allinea ruoli ed entitlement dal token. Ogni operazione protetta dichiara con la guardia `@SecurityAccess` l'entitlement (o il ruolo) che serve, letto dai claim del token. Swagger UI (springdoc) documenta e fa provare le API. Tutte le risposte, anche gli errori del filtro di sicurezza, usano l'envelope con status HTTP significativo. Dettagli in [research.md](research.md).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.0.8 (webmvc, actuator, security, oauth2-resource-server, data-mongodb, validation); springdoc-openapi 3.0.3; Nimbus JOSE+JWT (tramite il resource server) per chiave e token mock

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
| I. Qualità del codice | Controller in `v1.controller` senza logica; regole in `service`; repository in `repository`; DTO ↔ dominio in `v1.mapper`; mock nel package `mock`, attivo solo con il profilo `mocked` | ✅ |
| II. Test-first | Test di integrazione per ogni user story e caso limite, scritti prima del codice; `./gradlew build` in CI con Testcontainers | ✅ |
| III. Coerenza con i client | Contratto OpenAPI 3.1 in `contracts/` prima del codice; envelope unico anche per 401/403/404; status significativo (Q14); nessuna modifica incompatibile | ✅ |
| IV. Semplicità | Meccanismi standard di Spring Security (resource server) più una guardia dichiarativa di poche righe (`@SecurityAccess`); nessun catalogo di permessi; nessuna nuova infrastruttura oltre MongoDB già in compose | ✅ |
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
├── config/
│   ├── SecurityConfig.java              # resource server, envelope per 401/403
│   ├── OpenApiConfig.java               # Swagger UI, schema bearerAuth
│   ├── WebConfig.java                   # registra la guardia
│   ├── error/ErrorCode.java, BuonaCacciaException.java, GlobalExceptionHandler.java
│   └── mongo/CircuitBreakerService.java # Resilience4j su MongoDB
├── guard/SecurityAccess.java, SecurityGuardInterceptor.java
├── mock/                                # solo con il profilo mocked (findAll dal browser)
│   ├── MockAuthConfig.java              # chiave RSA, JwtEncoder, JwtDecoder
│   ├── MockAuthController.java          # findAll, jwks
│   ├── MockIdentityService.java         # upsert utenti e token in memoria
│   └── MockedIdentityProperties.java, MockedUsersLoader.java, MockUserResponse.java
├── model/                               # User, Census, enum, Roles, Entitlements
├── repository/UserRepository.java
├── service/UserService.java
│   └── guard/SecurityGuardService.java  # regola ruoli/entitlement della guardia
└── v1/
    ├── controller/UserController.java   # users/me
    ├── dto/ApiResponse.java, UserProfileDTO.java
    └── mapper/UserMapper.java

src/main/resources/application.yml, mocked-identity.yml

src/test/java/it/buonacaccia/
├── AbstractTest.java                    # crea il DB (Testcontainers) e avvia l'app, come in ScoutShopper-be
├── BuonaCacciaApplicationTest.java      # health
├── mock/
│   ├── ProbeController.java             # operazioni di prova per guardia ed envelope (profilo probe)
│   └── MockIdentityServiceTest.java
├── v1/controller/                       # MockAuthControllerTest, UserControllerTest, SecurityGuardTest,
│                                        # EnvelopeTest, MockProfileDisabledTest, OpenApiDocsTest
└── config/mongo/CircuitBreakerServiceTest.java

```

**Structure Decision**: applicazione Spring Boot unica con i package di AGENTS.md; dipendenze `v1` → `service` → `repository` → `model`, con `config` che collega i pezzi e `utils` condiviso. Tutto il mock (utenti di prova, emittente dei token, `findAll`, `jwks`) sta in `src/test`, come in ScoutShopper-be: il jar dell'applicazione non lo contiene, e in fase 6 resta solo per i test.

## Complexity Tracking

Nessuna violazione della costituzione da giustificare.
