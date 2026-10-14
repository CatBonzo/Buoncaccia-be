# Quickstart: verificare la feature 003

**Feature**: `003-iscrizioni` | **Contratto**: [contracts/enrollments.openapi.yaml](contracts/enrollments.openapi.yaml) | **Modello**: [data-model.md](data-model.md)

## Prerequisiti

- JDK 21, Docker in esecuzione; `docker compose up -d mongo`.
- Avvio con i mock: configurazione IntelliJ **BuonaCacciaApplication (test)** oppure `set -a; . test-src/env/test.env; set +a; ./gradlew bootRun`.
- Utenti di prova (in `mocked-identity.yml`, tutti censiti nell'anno corrente salvo dove indicato):

| Utente | Codice socio | Chi è |
| --- | --- | --- |
| `socio` | 100003 | Capo (CO) dell'unità `PIE-TO-01-EG` del gruppo `PIE-TO-01` |
| `esploratore` | 100006 | Ragazzo EG della stessa unità, 14 anni |
| `lupetto` | 100007 | Ragazzo LC di un'altra unità dello stesso gruppo |
| `rover` | 100008 | Ragazzo RS di Torino, 17 anni |
| `rover-lig` | 100009 | Ragazzo RS di un gruppo ligure |
| `capogruppo` | 100010 | Capo gruppo di `PIE-TO-01` |
| `socio-non-in-regola` | 100011 | Censimento non in regola |

```bash
tok() { curl -s localhost:8080/api/v1/mock/auth/findAll | jq -r ".payload[] | select(.username==\"$1\") | .accessToken"; }
SEG_PIE=$(tok segreteria); SEG_LIG=$(tok segreteria-lig); CAPO=$(tok socio); CAPOGRUPPO=$(tok capogruppo)
ROVER=$(tok rover); ROVER_LIG=$(tok rover-lig); ESPL=$(tok esploratore); NON_IN_REGOLA=$(tok socio-non-in-regola)
```

Per avere un evento con le iscrizioni aperte oggi, la segreteria del Piemonte crea e pubblica un evento `ROSS` con apertura iscrizioni a ieri, partenza fra un mese e 2 posti (scenari della fase 2). Per gli scenari del capo serve un secondo evento `CFM_EG` con modalità `BY_LEADER`.

## 1. Test automatici

```bash
./gradlew build
```

Atteso: build verde. I test di integrazione coprono US1–US5 e i casi limite. Quelli sul tempo (scadenza, rimborso, partenza) usano l'orologio controllato. Il test di concorrenza lancia 200 iscrizioni in parallelo e controlla che l'ordine di arrivo vada da 1 a 200 senza buchi (SC-002).

## 2. Scenari a mano

| # | Richiesta | Esito atteso |
| --- | --- | --- |
| 1 | `POST /api/v1/events/{ross}/enrollments` con `$ROVER` | 201, `status` `REGISTERED`, `position` `IN_SEATS`, `arrivalOrder` 1, `number` `PIE-<anno>-NNN-0001`, scadenza fra 9 giorni |
| 2 | Lo stesso con `$ROVER_LIG` | 201: la regione non conta (BR-15) |
| 3 | Lo stesso con `$CAPO` (terzo, posti = 2) | 201, `position` `WAITING`, `waitingPosition` 1, nessun adempimento `PAYMENT` |
| 4 | Di nuovo con `$ROVER` | 409, `40911` |
| 5 | Con `$ESPL` | 422, `42210`, motivi età e branca |
| 6 | Con `$NON_IN_REGOLA` | 422, `42210`, motivo `census` |
| 7 | `POST /api/v1/events/{cfm}/enrollments` con `$CAPO` e `{"memberCode":"100006"}` | 201, `presenterName` del capo, `LEADER_VALIDATION` `SATISFIED` |
| 8 | Lo stesso con `{"memberCode":"100007"}` (altra unità) | 403 |
| 9 | `GET /api/v1/enrollments/to-authorize` con `$CAPOGRUPPO`, poi `POST .../obligations/GROUP_LEADER_AUTHORIZATION/approve` | 200; l'adempimento diventa `SATISFIED` |
| 10 | `POST /api/v1/enrollments/{id}/obligations/PAYMENT/satisfy` con `$SEG_PIE` e una nota | 200; quando non resta nulla `status` `COMPLETED` |
| 11 | Lo stesso con `$SEG_LIG` | 403 |
| 12 | `POST /api/v1/enrollments/{rover}/withdraw` con `$ROVER` | 200, `WITHDRAWN`, `refundDue` true (partenza fra più di 7 giorni) |
| 13 | `GET` dell'iscrizione di `$CAPO` | `position` `IN_SEATS`, `promotedAt` valorizzato, `PAYMENT` `TODO`, nuova scadenza a 9 giorni |
| 14 | `GET /api/v1/enrollments/{id}` dell'iscrizione di `$ROVER_LIG` con `$ESPL` | 404 |
| 15 | `GET /api/v1/events/{ross}/enrollments?position=IN_SEATS` con `$SEG_PIE` | 200, iscritti in ordine di arrivo |
| 16 | `GET /api/v1/events?region=PIE` senza token | `enrolledCount` uguale alle iscrizioni attive |
| 17 | `POST /api/v1/events/{ross}/cancel` con motivo, poi `GET /api/v1/enrollments/mine` con `$ROVER_LIG` | Iscrizione `CANCELLED`, motivo `EVENT_CANCELLED`, `refundDue` true se la quota è maggiore di 0 |
| 18 | Swagger UI su `http://localhost:8080/swagger-ui.html` | Le nuove API sotto "enrollments" |

La scadenza a 9 giorni e la lista d'attesa alla partenza si verificano con i test automatici: a mano bisognerebbe aspettare giorni o il job ogni 5 minuti.
