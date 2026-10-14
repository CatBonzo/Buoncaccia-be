# Data Model: Notifiche e-mail

## outbox (`OutboxEvent`)

Evento di dominio in attesa di essere pubblicato su Kafka (R1).

| Campo | Tipo | Note |
| --- | --- | --- |
| id | String | UUID, è anche l'id del `DomainEvent` (chiave di idempotenza) |
| type | DomainEventType | vedi sotto |
| aggregateId | String | id dell'iscrizione, o dell'evento per `EVENT_CHANGED`; chiave del messaggio Kafka |
| payload | DomainEvent | snapshot completo (R3), salvato come sotto-documento |
| status | `PENDING` \| `PUBLISHED` | |
| createdAt | Instant | ordine di pubblicazione |
| publishedAt | Instant | indice TTL 7 giorni |
| attempts | int | tentativi di pubblicazione falliti |
| lastError | String | ultimo errore del relay |

Indici: `{status: 1, createdAt: 1}`; TTL su `publishedAt` (604800 s).

`DomainEventType`: `ENROLLMENT_REGISTERED`, `AUTHORIZATION_REQUESTED`, `ENROLLMENT_COMPLETED`, `ENROLLMENT_EXPIRED`, `ENROLLMENT_WITHDRAWN`, `ENROLLMENT_PROMOTED`, `ENROLLMENT_REJECTED`, `ENROLLMENT_CANCELLED`, `DEADLINE_REMINDER`, `EVENT_CHANGED`.

## DomainEvent (messaggio Kafka, record)

| Campo | Tipo | Note |
| --- | --- | --- |
| id | String | uguale a `OutboxEvent.id` |
| type | DomainEventType | |
| occurredAt | Instant | |
| enrollment | EnrollmentSnapshot | assente per `EVENT_CHANGED` |
| event | EventSnapshot | sempre |
| obligationType | ObligationType | solo `AUTHORIZATION_REQUESTED` |
| reminderDays | Integer | solo `DEADLINE_REMINDER` (3 o 1) |

**EnrollmentSnapshot**: `id`, `number`, `participantId`, `participantName`, `participantUnitId`, `participantGroupId`, `presenterId`, `presenterName`, `status`, `position`, `waitingRank` (posizione in lista, solo in attesa), `pendingObligations` (`List<ObligationType>` ancora `TODO`), `deadline`, `fee`, `refundDue`, `closingReason`, `closingNote`.

**EventSnapshot**: `id`, `code`, `title`, `startDate`, `endDate`, `location`, `changedFields` (solo `EVENT_CHANGED`: `startDate`, `endDate`, `location`).

## notifications (`Notification`)

Un avviso a un destinatario (FR-006). È anche la chiave di idempotenza del consumer (R4).

| Campo | Tipo | Note |
| --- | --- | --- |
| id | String | |
| domainEventId | String | |
| domainEventType | DomainEventType | |
| template | NotificationTemplate | |
| recipientId | String | id utente; `"-"` per `NO_RECIPIENT` |
| recipientRole | `PARTICIPANT` \| `PRESENTER` \| `UNIT_LEADER` \| `GROUP_LEADER` | |
| email | String | indirizzo usato, null se `NO_ADDRESS` |
| subject | String | |
| enrollmentId | String | indice; null per le notifiche di solo evento |
| eventId | String | |
| status | NotificationStatus | `PENDING`, `SENT`, `FAILED`, `NO_ADDRESS`, `NO_RECIPIENT` |
| attempts | int | |
| lastError | String | |
| createdAt, sentAt, updatedAt | Instant | |

Indici: unico `{domainEventId: 1, recipientId: 1, template: 1}`; `{enrollmentId: 1, createdAt: 1}`; `{status: 1}`.

`NotificationTemplate`: `ENROLLMENT_CONFIRMATION`, `PRESENTER_NOTICE`, `AUTHORIZATION_REQUEST`, `DEADLINE_REMINDER`, `ENROLLMENT_COMPLETED`, `ENROLLMENT_EXPIRED`, `ENROLLMENT_WITHDRAWN`, `ENROLLMENT_PROMOTED`, `ENROLLMENT_REJECTED`, `ENROLLMENT_CANCELLED`, `EVENT_CHANGED`. Per i cambi di stato il presentatore riceve lo stesso modello del partecipante, con `recipientRole = PRESENTER`; la chiave unica comprende `recipientId`, quindi restano due notifiche distinte.

## authorization_links (`AuthorizationLink`)

| Campo | Tipo | Note |
| --- | --- | --- |
| id | String | |
| tokenHash | String | SHA-256 esadecimale del token; unico |
| enrollmentId | String | |
| obligationType | ObligationType | `UNIT_LEADER_AUTHORIZATION` o `GROUP_LEADER_AUTHORIZATION` |
| leaderId | String | capo destinatario |
| domainEventId | String | con `leaderId` unico: un evento ripetuto non crea un secondo link |
| createdAt | Instant | |
| usedAt | Instant | null finché non usato |
| decision | `APPROVED` \| `REJECTED` | |

Validità (FR-017), calcolata a ogni lettura: `usedAt == null` ∧ iscrizione `active` ∧ adempimento `obligationType` in stato `TODO` ∧ `now < enrollment.deadline`.

Indici: unico `tokenHash`; unico `{domainEventId: 1, leaderId: 1}`; `enrollmentId`.

## enrollments (modifica)

| Campo | Tipo | Note |
| --- | --- | --- |
| remindersSent | `Set<String>` | chiavi `D3:<deadline ISO>`, `D1:<deadline ISO>` già inviate (R9) |

## Transizioni → eventi di dominio

| Da (servizio fase 3) | Evento di dominio | Più |
| --- | --- | --- |
| `enroll` | `ENROLLMENT_REGISTERED` | un `AUTHORIZATION_REQUESTED` per ogni autorizzazione `TODO` (nei posti; in attesa solo quelle già `TODO`) |
| `approve`/`satisfy` che completa | `ENROLLMENT_COMPLETED` | |
| `expireOverdue` | `ENROLLMENT_EXPIRED` | la promozione che segue produce il suo evento |
| `withdraw` | `ENROLLMENT_WITHDRAWN` (con `refundDue`) | |
| `promoteWaiting` | `ENROLLMENT_PROMOTED` | `AUTHORIZATION_REQUESTED` per le autorizzazioni `TODO` |
| `reject` | `ENROLLMENT_REJECTED` (con motivo) | |
| `cancelWaitingAtStart`, `cancelForEvent` | `ENROLLMENT_CANCELLED` (`closingReason` `SEATS_EXHAUSTED` o `EVENT_CANCELLED`, `refundDue`) | |
| job promemoria | `DEADLINE_REMINDER` | |
| `EventService.update` con date o luogo cambiati, evento pubblicato | `EVENT_CHANGED` | |
