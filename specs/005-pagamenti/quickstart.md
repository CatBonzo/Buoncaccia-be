# Quickstart: Pagamenti e rimborsi

Prove manuali della feature 005 sul PC, con i container di `docker-compose.yaml`. I test automatici (`./gradlew build`) usano invece Testcontainers.

## Prerequisiti

```bash
docker compose up -d          # MongoDB (replica set), Kafka, Mailpit
```

Avviare `BuonaCacciaApplication (mocked)`; casella di prova su http://localhost:8025; token dei mock da `GET /api/v1/mock/auth/findAll`.

## 1. Coordinate e quota (US5, US1)

1. Con `segreteria` (PIE): `PUT /api/v1/region-settings/PIE` con `{"holder":"AGESCI Piemonte","iban":"IT60X0542811101000000123456","contactEmail":"segreteria@buonacaccia.local","hours":"lun-ven 9-13"}`.
2. Con `rover` iscriversi a un evento PIE con quota (quickstart della 003) e leggere `GET /api/v1/enrollments/{id}`: `payment` (fee, paid 0, due = fee) e `paymentInfo` con l'IBAN.

## 2. Registrare un pagamento (US2)

1. Con `segreteria`: `POST /api/v1/enrollments/{id}/payments` con `{"amount":50.00,"method":"BANK_TRANSFER","receivedOn":"<oggi>","reference":"CRO123"}`: il residuo scende; la stessa richiesta due volte → 409 `40930`.
2. Registrare il resto: l'adempimento di pagamento è soddisfatto e, se non resta altro, l'iscrizione è `COMPLETED`; in Mailpit arriva l'e-mail al socio per ogni pagamento.
3. `POST /api/v1/payments/{id}/cancel` con un motivo: il residuo risale e l'adempimento torna da fare.

## 3. Rimborso (US3)

1. Con `rover` ritirare l'iscrizione pagata (almeno 7 giorni prima della partenza): `POST /api/v1/enrollments/{id}/withdraw`; in Mailpit arrivano le e-mail di ritiro e di richiesta di rimborso.
2. Con `segreteria`: `GET /api/v1/refunds?status=REQUESTED`, poi `POST /api/v1/refunds/{id}/take`, poi `.../issue` con `{"issuedOn":"<oggi>","reference":"CRO987"}`; l'e-mail di rimborso emesso arriva al socio. Un secondo `issue` → 409 `40940`.

## 4. Riconciliazione (US4)

`GET /api/v1/events/{eventId}/reconciliation` per i totali; `GET /api/v1/events/{eventId}/reconciliation/export` per il CSV (apribile in Excel).

## Esito atteso

`./gradlew build --max-workers=1` verde, comprese le prove su rate, doppioni, concorrenza e transizioni del rimborso.
