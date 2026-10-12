# Quickstart: verificare la feature 001

**Feature**: `001-identita-ruoli` | **Contratto**: [contracts/identity.openapi.yaml](contracts/identity.openapi.yaml) | **Modello**: [data-model.md](data-model.md)

## Prerequisiti

- JDK 21, Docker in esecuzione.
- Infrastruttura locale: `docker compose up -d` (serve MongoDB).

## 1. Test automatici

```bash
./gradlew build
```

Atteso: build verde. I test di integrazione avviano da soli un MongoDB con Testcontainers e coprono US1–US4 e i casi limite del file degli utenti di prova.

## 2. Avvio in modalità mock

Da IntelliJ con le variabili di `test-src/env/mocked.env` (o `test.env`: database separato e log dettagliati), oppure:

```bash
set -a; . test-src/env/mocked.env; set +a
./gradlew bootRun
```

## 3. Scenari a mano

| # | Richiesta | Esito atteso |
| --- | --- | --- |
| 1 | `curl -s localhost:8080/api/v1/mock/auth/findAll` | 200, `errorCode` 0, quattro utenti, ognuno con `issuer` = `mock-auth` e `accessToken` |
| 2 | `curl -s localhost:8080/api/v1/mock/auth/jwks` | 200, JWK Set con una chiave RSA |
| 3 | `curl -s -H "Authorization: Bearer $TOKEN_SOCIO" localhost:8080/api/v1/users/me` | 200, `member` = true, `census.memberCode` valorizzato, ruolo `agesci:socio:user` |
| 4 | Come il 3 con il token di `esterno` | 200, `member` = false, `census` = null |
| 5 | `curl -s localhost:8080/api/v1/users/me` (senza token) | 401, `errorCode` 40100, `errorDomain` `AUTH` |
| 6 | Come il 3 con un carattere del token cambiato | 401, `errorCode` 40100 |
| 7 | Riavviare il backend e ripetere il 3 con il vecchio token | 401; `findAll` restituisce token nuovi |
| 8 | `curl -s -H "Authorization: Bearer $TOKEN_SOCIO" localhost:8080/api/v1/nonesiste` (senza token è 401) | 404, `errorCode` 40400, stesso envelope |
| 10 | Aprire `http://localhost:8080/swagger-ui.html`, "Authorize" con un token | Swagger UI con gli endpoint e le prove dal browser |
| 9 | Avviare senza il profilo `mocked` e chiamare `findAll` | 404 |

Per prendere un token da riga di comando:

```bash
TOKEN_SOCIO=$(curl -s localhost:8080/api/v1/mock/auth/findAll | jq -r '.payload[] | select(.username=="socio") | .accessToken')
```

La guardia `@SecurityAccess` (US3) non protegge ancora un endpoint reale con entitlement: la verifica il test di integrazione con operazioni di prova; i primi endpoint veri arrivano con il catalogo eventi (fase 2).
