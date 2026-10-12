# Buona Caccia – backend

Backend per la gestione delle iscrizioni agli eventi AGESCI, sviluppato in modalità spec-driven (progetto di tesi).

## Stack

- Java 21, Spring Boot 4.0
- Gradle 9 (wrapper incluso, `build.gradle` in Groovy), versioni in `gradle/libs.versions.toml`

Contesto, regole di lavoro e scelte prese sono in [AGENTS.md](AGENTS.md).

## Struttura

```
src/main/java/it/buonacaccia/
├── BuonaCacciaApplication.java   classe principale
├── config/                       configurazione Spring (error: errori ed envelope, mongo: circuit breaker)
├── guard/                        guardia @SecurityAccess
├── model/                        modello di dominio (documenti MongoDB)
├── repository/                   repository Spring Data MongoDB
├── service/                      logica di business (guard: regole della guardia)
└── v1/                           API v1: controller, dto, mapper
src/main/resources/application.yml
src/test/java/                    test, mock (package mock), AbstractTest
```

## Ambiente locale

```bash
docker compose up -d     # MongoDB (27017), Kafka (9092), Mailpit (1025, web su http://localhost:8025)
docker compose down      # ferma i container (i dati restano)
docker compose down -v   # ferma e cancella i dati
```

Per avviare l'applicazione con utenti e token di prova contro questi container, lanciare da IntelliJ `BuonaCacciaApplication` con le variabili di `test-src/env/mocked.env` (profilo `mocked`). Poi dal browser: http://localhost:8080/api/v1/mock/auth/findAll. Senza il profilo `mocked` il mock non si carica.

## Comandi

```bash
./gradlew build      # compila e lancia i test
./gradlew bootRun      # avvia l'applicazione (senza mock: serve Keycloak, fase 6)
./gradlew bootRun      # con mocked.env caricato avvia l'applicazione con i mock su http://localhost:8080
```

`GET /actuator/health` risponde con lo stato dell'applicazione.

Con il profilo `mocked` (`test-src/env/mocked.env` o `test.env`): utenti e token di prova su `GET /api/v1/mock/auth/findAll`, profilo su `GET /api/v1/users/me`, documentazione su `http://localhost:8080/swagger-ui.html`.

## IntelliJ IDEA

Aprire la cartella del progetto: IntelliJ riconosce Gradle. In *Settings > Build Tools > Gradle* usare come Gradle JVM il JDK 21.
