# Quickstart: Notifiche e-mail

Prove manuali della feature 004 sul PC, con i container di `docker-compose.yaml`. I test automatici (`./gradlew build`) usano invece Testcontainers con Kafka e Mailpit propri.

## Prerequisiti

```bash
docker compose up -d          # MongoDB (replica set), Kafka 4.3, Mailpit
```

- Avviare `BuonaCacciaApplication (mocked)` da IntelliJ (env `test-src/env/mocked.env`, che contiene già `SPRING_KAFKA_BOOTSTRAP_SERVERS` e `SPRING_MAIL_HOST/PORT`).
- Casella di prova: http://localhost:8025 (Mailpit).
- Token dei mock: `GET http://localhost:8080/api/v1/mock/auth/findAll`.

## 1. Conferma di iscrizione (US1)

1. Con un evento pubblicato e iscrizioni aperte (vedi quickstart della 003), iscrivere `rover` (`POST /api/v1/events/{id}/enrollments`, corpo vuoto).
2. Entro qualche secondo in Mailpit compare un'e-mail a `rover` con oggetto che contiene il numero dell'iscrizione, "nei posti", adempimenti e scadenza.
3. Iscrivere `esploratore` con il token di un capo della sua unità (`{"memberCode":"100006"}`): due e-mail, al ragazzo e al capo.

## 2. Autorizzazione dal link (US2)

1. Iscrivere un partecipante a un evento con l'adempimento di autorizzazione del capo unità.
2. In Mailpit aprire l'e-mail al capo e copiare il token dal link (`.../autorizza/{token}`).
3. `GET /api/v1/authorization-links/{token}` (senza token di accesso): 200 con evento e partecipante.
4. `POST /api/v1/authorization-links/{token}/approve`: 200, `decision: APPROVED`.
5. Ripetere il punto 4: 404 `40420`.

## 3. Registro e e-mail mancanti (US6)

1. `GET /api/v1/enrollments/{id}/notifications` con il token di `segreteria-pie`: elenco degli avvisi con stato `SENT`.
2. Fermare Kafka (`docker compose stop kafka`), iscrivere un socio: l'iscrizione riesce (201), nessuna e-mail.
3. `docker compose start kafka`: l'e-mail arriva entro qualche secondo dopo la ripresa, una sola volta.

## 4. Promemoria e modifiche (US3–US5)

- Promemoria: con il profilo `mocked` impostare `buonacaccia.jobs.enrollments-delay=PT10S` e un evento con scadenza vicina; i promemoria D3 e D1 arrivano una volta ciascuno.
- Modifica del luogo di un evento pubblicato (`PUT /api/v1/events/{id}`): un'e-mail a ogni iscritto attivo. Cambiare solo la descrizione: nessuna e-mail.

## Esito atteso

Tutti i test di `./gradlew build --max-workers=1` verdi, compresi quelli che fermano il relay e l'invio (US6).
