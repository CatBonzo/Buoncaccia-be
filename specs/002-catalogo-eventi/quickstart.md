# Quickstart: verificare la feature 002

**Feature**: `002-catalogo-eventi` | **Contratto**: [contracts/catalog.openapi.yaml](contracts/catalog.openapi.yaml) | **Modello**: [data-model.md](data-model.md)

## Prerequisiti

- JDK 21, Docker in esecuzione; `docker compose up -d mongo`.
- Avvio con i mock: configurazione IntelliJ **BuonaCacciaApplication (test)** oppure `set -a; . test-src/env/test.env; set +a; ./gradlew bootRun`.
- Token da `findAll` (vedi la feature 001):

```bash
tok() { curl -s localhost:8080/api/v1/mock/auth/findAll | jq -r ".payload[] | select(.username==\"$1\") | .accessToken"; }
ADMIN=$(tok amministratore); SEG_PIE=$(tok segreteria); SEG_LIG=$(tok segreteria-lig); SOCIO=$(tok socio)
```

## 1. Test automatici

```bash
./gradlew build
```

Atteso: build verde. I test di integrazione coprono US1–US5 e i casi limite; quelli sul tempo (US3) usano un orologio controllato, compreso il cambio dell'ora legale.

## 2. Scenari a mano

| # | Richiesta | Esito atteso |
| --- | --- | --- |
| 1 | `GET /api/v1/regions` senza token | 200, lista delle regioni con `PIE` e `LIG` |
| 2 | `GET /api/v1/event-types` senza token | 200, i tipi di prova `CFM_EG`, `ROSS`, `CFA` |
| 3 | `POST /api/v1/event-types` con `$SEG_PIE` | 403, `40300` |
| 4 | `POST /api/v1/events` con `$SEG_PIE`, `typeCode: CFM_EG`, `level: REGIONAL`, `region: PIE` | 201, `status` `DRAFT`, `code` `PIE-<anno>-001`, requisiti e adempimenti del tipo |
| 5 | Lo stesso con `region: LIG` | 403 |
| 6 | `GET /api/v1/events/{id}` della bozza senza token | 404; con `$SEG_PIE` 200 |
| 7 | `POST /api/v1/events/{id}/publish` su una bozza incompleta | 422, `42201`, `payload` con l'elenco dei campi |
| 8 | Completare la bozza (`PUT` con `version`) e ripubblicare | 200, stato secondo le date (es. `PUBLISHED` e `availability` `NOT_YET_OPEN`) |
| 9 | `GET /api/v1/events?region=PIE&sort=startDate,asc&size=10` senza token | 200, pagina con l'evento e `totalElements` |
| 10 | `PUT` dello stesso evento con una `version` vecchia | 409, `40903` |
| 11 | `PUT` che cambia `region` su un evento pubblicato | 409, `40904` |
| 12 | `POST /api/v1/events/{id}/cancel` senza `reason` | 422; con motivo 200 e `status` `CANCELLED` |
| 13 | `GET /api/v1/events/managed` con `$SEG_LIG` | 200, nessun evento del Piemonte |
| 14 | Swagger UI su `http://localhost:8080/swagger-ui.html` | Le nuove API con "Authorize" |

Per lo scenario 4 basta il corpo minimo `{"typeCode":"CFM_EG","level":"REGIONAL","region":"PIE"}`: in bozza il resto è facoltativo.
