# Data Model: Pagamenti e rimborsi

Importi sempre in centesimi interi (`long`) nel database (R1); le API usano euro con due decimali.

## payments (`Payment`)

| Campo | Tipo | Note |
| --- | --- | --- |
| id | String | UUID |
| enrollmentId | String | indice |
| enrollmentNumber | String | copia per elenchi e CSV |
| eventId, eventCode | String | `eventId` indicizzato per la riconciliazione |
| eventLevel, eventRegion | EventLevel, Region | copiati dall'iscrizione per la regola territoriale |
| participantId | String | |
| amountCents | long | > 0 |
| method | `BANK_TRANSFER` (per ora l'unico) | |
| receivedOn | LocalDate | non nel futuro (ora italiana) |
| reference | String | facoltativo, ≤ 100 caratteri (es. CRO) |
| status | `VALID` \| `CANCELLED` | |
| registeredBy, registeredAt | String, Instant | |
| cancelledBy, cancelledAt, cancelReason | String, Instant, String | solo se `CANCELLED`; motivo ≤ 500 |

Indici: `{enrollmentId: 1, registeredAt: 1}`, `{eventId: 1}`.

## refunds (`Refund`)

| Campo | Tipo | Note |
| --- | --- | --- |
| id | String | |
| enrollmentId | String | indice **unico**: un solo rimborso per iscrizione |
| enrollmentNumber, participantId, participantName, presenterId | String | |
| eventId, eventCode, eventLevel, eventRegion | | come in `Payment` |
| amountCents | long | versato valido alla chiusura |
| closingReason | ClosingReason | perché l'iscrizione si è chiusa |
| status | `REQUESTED` \| `IN_PROGRESS` \| `ISSUED` \| `REJECTED` | |
| createdAt | Instant | |
| takenBy, takenAt | String, Instant | presa in carico |
| issuedOn | LocalDate | data in cui il rimborso è stato fatto |
| reference | String | facoltativo |
| rejectReason | String | solo `REJECTED`, ≤ 500 |
| processedBy, processedAt | String, Instant | chi ha chiuso (`ISSUED`/`REJECTED`) |

Indici: `enrollmentId` unico, `{status: 1, createdAt: 1}`, `{eventId: 1}`.

Transizioni: `REQUESTED → IN_PROGRESS`; `REQUESTED|IN_PROGRESS → ISSUED`; `REQUESTED|IN_PROGRESS → REJECTED`. Nessuna uscita da `ISSUED` e `REJECTED`.

## region_settings (`RegionSettings`)

| Campo | Tipo | Note |
| --- | --- | --- |
| id | String | sigla della regione (`PIE`…) oppure `NAT` per l'amministrazione nazionale |
| holder | String | intestatario del conto, ≤ 120 |
| iban | String | validato (R10), maiuscolo senza spazi |
| contactEmail, contactPhone | String | |
| hours | String | orari della segreteria, ≤ 300 |
| updatedBy, updatedAt | String, Instant | |

## enrollments (modifica)

| Campo | Tipo | Note |
| --- | --- | --- |
| feeCents | Long | quota dovuta all'iscrizione (R3); null nei documenti vecchi = quota dell'evento |
| paidCents | long | somma dei pagamenti `VALID`; 0 di partenza |

Residuo = `feeCents − paidCents`. Con l'adempimento `PAYMENT`: `SATISFIED` se e solo se `paidCents ≥ feeCents` (oltre a `satisfy` manuale della segreteria, R4).

## Transizioni → eventi di dominio

| Operazione | Evento di dominio | Più |
| --- | --- | --- |
| `PaymentService.register` | `PAYMENT_REGISTERED` (importo, versato, dovuto) | `ENROLLMENT_COMPLETED` se l'iscrizione si completa |
| `PaymentService.cancel` | — (nessuna e-mail) | |
| chiusura con versato > 0 e diritto | `REFUND_REQUESTED` (importo) | |
| `RefundService.issue` | `REFUND_ISSUED` (importo, data, riferimento) | |
| `RefundService.reject` | `REFUND_REJECTED` (importo, motivo) | |
| `RefundService.take` | — | |
