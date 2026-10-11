# Buona Caccia – backend

Backend per la gestione delle iscrizioni agli eventi AGESCI, sviluppato in modalità spec-driven (progetto di tesi).

## Stack

- Java 21, Spring Boot 4.0, Spring Cloud 2025.1
- Gradle 9 (wrapper incluso, file `build.gradle` in Groovy), versioni centralizzate in `gradle/libs.versions.toml`
- Convenzioni comuni dei servizi in `buildSrc` (plugin `buonacaccia.spring-service`)

Contesto, regole di lavoro e scelte prese sono in [AGENTS.md](AGENTS.md).

## Moduli

| Modulo | Porta | Responsabilità |
| --- | --- | --- |
| `api-gateway` | 8080 | Instradamento delle richieste `/api/v1/**` verso i servizi |
| `identity-service` | 8081 | Utenti, `/users/me`, auth mock |
| `event-service` | 8082 | Tipi evento, eventi, catalogo |
| `enrollment-service` | 8083 | Iscrizioni, posti, lista d'attesa |
| `notification-service` | 8084 | Notifiche e-mail dagli eventi Kafka |

## Comandi

```bash
./gradlew build                          # compila e lancia i test di tutti i moduli
./gradlew :identity-service:bootRun      # avvia un singolo servizio
```

Ogni servizio espone `GET /actuator/health`.

## IntelliJ IDEA

Aprire la cartella del progetto: IntelliJ riconosce Gradle e importa i moduli. In *Settings > Build Tools > Gradle* usare come Gradle JVM il JDK 21.
