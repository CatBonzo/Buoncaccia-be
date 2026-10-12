# Tasks: Identità, ruoli e autenticazione mock

**Feature**: `001-identita-ruoli` | **Input**: [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/)

Scritto il 12/10 insieme all'implementazione, dopo la richiesta di Mahima di portare in questo branch guardie, Swagger e `test.env` sul modello di ScoutShopper-be. I test di ogni fase sono stati scritti prima del codice (costituzione, principio II).

## Fase 1: setup

- [x] T001 Dipendenze in `build.gradle` e `gradle/libs.versions.toml`: security-oauth2-resource-server, data-mongodb, validation, springdoc 3.0.3; test con Testcontainers
- [x] T002 `application.yml` (MongoDB, resource server Keycloak, springdoc), `application-mocked.yml`, `application-test.yml`
- [x] T003 `test-src/env/test.env` con profili `mocked,test` e database `buonacaccia-test` (R12)

## Fase 2: fondamenta

- [x] T004 [P] Test di supporto: `ApiTestSupport`, `TestcontainersConfiguration`
- [x] T005 [P] Catalogo errori `utils/ErrorCode`, envelope `api/dto/ApiResponse`, `service/exception/BuonaCacciaException`
- [x] T006 [P] Modello `User`, `Census`, enum, costanti `Roles` ed `Entitlements`; `UserRepository`
- [x] T007 `config/SecurityConfig` (resource server, envelope per 401/403), `api/GlobalExceptionHandler`

## Fase 3: US1 token di prova (P1)

- [x] T008 Test `MockAuthControllerTest`, `MockProfileDisabledTest`, `MockIdentityServiceTest`
- [x] T009 `mocked-identity.yml` con i quattro utenti, `config/MockedIdentityProperties`
- [x] T010 `config/MockAuthConfig` (chiave RSA, encoder, decoder), `service/MockIdentityService`, `mockmigration/MockedUsersLoader`
- [x] T011 `api/MockAuthController` (`findAll`, `jwks`), `mapper/UserMapper`

## Fase 4: US2 profilo (P1)

- [x] T012 Test `UserControllerTest` (profili, token alterati, scaduti, di altro emittente, utente nuovo)
- [x] T013 `service/UserService` (upsert, `syncFromToken`), `api/UserController` (`/users/me`)

## Fase 5: US3 guardia (P2)

- [x] T014 Test `SecurityGuardTest` con `ProbeController` solo nei test (profilo `probe`)
- [x] T015 `config/guard/SecurityAccess`, `config/guard/SecurityGuardInterceptor`, `config/WebConfig`

## Fase 6: US4 envelope (P2)

- [x] T016 Test `EnvelopeTest` (successo, 404, 500 senza dettagli interni)

## Fase 7: documentazione API

- [x] T017 Test `OpenApiDocsTest`; `config/OpenApiConfig` con schema `bearerAuth`

## Fase 8: chiusura

- [x] T018 `./gradlew build` verde (31 test), prova a mano con `test.env`
- [x] T019 AGENTS.md, diario, quickstart
- [x] T021 Mock in `src/test` (package `mock`), `AbstractTest` che crea il DB, `TestBuonaCacciaApplication`
- [x] T020 Test `CircuitBreakerServiceTest`; Lombok, log e circuit breaker su MongoDB (R13)
