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
├── api/                          controller REST e DTO
├── config/                       configurazione Spring
├── job/                          attività pianificate
├── mapper/                       conversioni dominio <-> DTO
├── mockmigration/                dati di prova all'avvio
├── model/                        modello di dominio (documenti MongoDB)
├── repository/nosql/             repository Spring Data MongoDB
├── service/                      logica di business
└── utils/                        utilità
src/main/resources/application.yml
src/test/java/                    test
```

## Ambiente locale

```bash
docker compose up -d     # MongoDB (27017), Kafka (9092), Mailpit (1025, web su http://localhost:8025)
docker compose down      # ferma i container (i dati restano)
docker compose down -v   # ferma e cancella i dati
```

Per avviare l'applicazione contro questi container, caricare in IntelliJ le variabili di `test-src/env/mocked.env`.

## Comandi

```bash
./gradlew build      # compila e lancia i test
./gradlew bootRun    # avvia l'applicazione su http://localhost:8080
```

`GET /actuator/health` risponde con lo stato dell'applicazione.

## IntelliJ IDEA

Aprire la cartella del progetto: IntelliJ riconosce Gradle. In *Settings > Build Tools > Gradle* usare come Gradle JVM il JDK 21.
