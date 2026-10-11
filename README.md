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

## Comandi

```bash
./gradlew build      # compila e lancia i test
./gradlew bootRun    # avvia l'applicazione su http://localhost:8080
```

`GET /actuator/health` risponde con lo stato dell'applicazione.

## IntelliJ IDEA

Aprire la cartella del progetto: IntelliJ riconosce Gradle. In *Settings > Build Tools > Gradle* usare come Gradle JVM il JDK 21.
