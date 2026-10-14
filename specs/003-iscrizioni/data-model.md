# Data model: Iscrizioni e lista d'attesa

**Feature**: `003-iscrizioni` | **Data**: 2026-10-14 | **Plan**: [plan.md](plan.md)

Nel codice e nel JSON i nomi sono in inglese (regola di AGENTS.md); fra parentesi c'è il nome usato nella spec. I documenti sono classi Lombok (`@AllArgsConstructor @NoArgsConstructor @Getter @Setter @ToString @Builder`); i sotto-documenti hanno anche `@EqualsAndHashCode`.

## Modifiche a entità esistenti

| Entità | Campo | Tipo | Note |
| --- | --- | --- | --- |
| `User` | `birthDate` | LocalDate | Serve per l'età alla partenza (BR-06); se manca e l'evento ha limiti di età, è un motivo di rifiuto |
| `Census` | `groupLeader` | boolean | Capo gruppo del proprio `groupId` (FR-009) |
| `Event` | ~~`enrolledCount`~~ | — | Rimosso: il numero di iscritti viene da `EventCapacity.active` (R2) |

## EventCapacity (Capienza) – collezione `event_capacity`

| Campo | Tipo | Regole |
| --- | --- | --- |
| `id` | string, `@Id` | = id dell'evento |
| `seats` | int | Copia di `Event.seats`, aggiornata dal cambio posti (R10) |
| `lastArrival` | long | Ultimo ordine di arrivo assegnato, cresce di 1 a ogni iscrizione |
| `inSeats` | int | Iscrizioni attive nei posti, mai più di `seats` |
| `waiting` | int | Iscrizioni attive in attesa |
| `active` | int | `inSeats + waiting`; è il contatore mostrato nel catalogo |

Viene creata alla pubblicazione con `$setOnInsert`, oppure alla prima iscrizione se manca. Si modifica solo con `findAndModify` condizionati (R1), mai con `save`.

## Enrollment (Iscrizione) – collezione `enrollments`

| Campo | Tipo | Regole |
| --- | --- | --- |
| `id` | string (UUID), `@Id` | Generato |
| `number` | string | `<codice evento>-<arrivo su 4 cifre>`, es. `PIE-2027-001-0031`, univoco (FR-006) |
| `eventId`, `eventCode`, `eventTypeCode` | string | Copiati dall'evento: servono agli elenchi e al controllo "uno per tipo" |
| `eventTitle`, `eventStartDate` | string, LocalDate | Copiati per gli elenchi personali senza join |
| `eventRegion` | enum `Region` | Solo informativo (BR-15); null per gli eventi nazionali |
| `participantId` | string | Id `User` del partecipante |
| `participantMemberCode`, `participantName` | string | Copiati dal censimento all'iscrizione |
| `participantGroupId`, `participantUnitId` | string | Servono a `maxPerGroup` e alle autorizzazioni |
| `presenterId`, `presenterName` | string | Il capo che ha iscritto il ragazzo; null per l'iscrizione autonoma |
| `status` | enum `EnrollmentStatus` | Vedi il ciclo di vita |
| `position` | enum `SeatPosition` | `IN_SEATS` o `WAITING` |
| `arrivalOrder` | long | Unico per evento, dalla capienza (FR-005) |
| `active` | boolean | true se `status` è `REGISTERED` o `COMPLETED` |
| `obligations` | lista `EnrollmentObligation` | Vedi sotto |
| `registeredAt` | Instant | Istante dell'iscrizione |
| `deadline` | Instant | `registeredAt` + 9 giorni (BR-04); alla promozione, istante della promozione + 9 giorni (FR-017) |
| `promotedAt` | Instant | Se l'iscrizione era in attesa ed è passata nei posti |
| `completedAt` | Instant | Quando diventa `COMPLETED` |
| `closedAt`, `closingReason` | Instant, enum `ClosingReason` | Quando esce dagli stati attivi |
| `closingNote` | string | Motivo del diniego o dell'annullamento, max 500 |
| `refundDue` | Boolean | null finché l'iscrizione è attiva; calcolato al ritiro e all'annullamento (R9) |
| `version` | Long, `@Version` | Protegge da modifiche concorrenti (409 `40903`) |
| `createdAt`, `updatedAt` | Instant | Gestiti dal sistema |

### Indici

| Indice | Scopo |
| --- | --- |
| `{eventId: 1, arrivalOrder: 1}` univoco | Ordine di arrivo unico; promozione del primo in attesa |
| `{eventId: 1, participantId: 1}` univoco parziale, `active: true` | Una sola iscrizione attiva per socio ed evento (R3) |
| `{participantId: 1}`, `{presenterId: 1}` | "Le mie iscrizioni" e "iscrizioni presentate" |
| `{participantUnitId: 1, status: 1}`, `{participantGroupId: 1, status: 1}` | Iscrizioni da autorizzare per il capo unità e il capo gruppo |
| `{status: 1, deadline: 1}` | Job delle scadenze |

### EnrollmentObligation (Adempimento dell'iscrizione) – sotto-documento

| Campo | Tipo | Regole |
| --- | --- | --- |
| `type` | enum `ObligationType` | Lo stesso della fase 2 |
| `description` | string | Copiata dall'adempimento dell'evento |
| `status` | enum `ObligationStatus` | `TODO`, `SATISFIED`, `REJECTED`, `NOT_REQUIRED` |
| `satisfiedBy` | string | Id `User` di chi l'ha soddisfatto o negato |
| `satisfiedAt` | Instant | Quando |
| `note` | string | Nota della segreteria o motivo del diniego, max 500 |

Quali adempimenti nascono e in che stato è scritto in research R7.

## Enum nuovi

| Enum | Valori |
| --- | --- |
| `EnrollmentStatus` | `REGISTERED` (registrata), `COMPLETED` (completa), `EXPIRED` (scaduta), `WITHDRAWN` (ritirata), `REJECTED` (rifiutata), `CANCELLED` (annullata) |
| `SeatPosition` | `IN_SEATS` (nei posti), `WAITING` (in attesa) |
| `ObligationStatus` | `TODO`, `SATISFIED`, `REJECTED`, `NOT_REQUIRED` |
| `ClosingReason` | `WITHDRAWN`, `EXPIRED`, `REJECTED`, `EVENT_CANCELLED`, `SEATS_EXHAUSTED` |

## Ciclo di vita dell'iscrizione

```text
                 ┌───────── tutti gli adempimenti soddisfatti ─────────┐
                 │                                                     ▼
 (iscrizione) → REGISTERED ──────────────────────────────────────→ COMPLETED
                 │  ▲                                                  │
                 │  └──── promozione con quota > 0 (aggiunge PAYMENT) ──┘
                 │
  REGISTERED o COMPLETED →  WITHDRAWN  (ritiro prima di startsAt)
                         →  REJECTED   (diniego di un'autorizzazione)
                         →  CANCELLED  (evento annullato; in attesa alla partenza)
  solo REGISTERED        →  EXPIRED    (deadline passata, job)
```

- `WITHDRAWN`, `EXPIRED`, `REJECTED` e `CANCELLED` sono stati finali: l'iscrizione non è più attiva (`active = false`) e libera il suo posto (o la sua posizione in attesa) nella capienza.
- Ogni transizione non prevista risponde 409 `40910`.
- La posizione va solo da `WAITING` a `IN_SEATS` (promozione), mai al contrario.
- Una riduzione dei posti sotto `inSeats` viene rifiutata (R10), quindi nessuno torna in attesa.

## Valori calcolati in lettura (EnrollmentDTO)

| Campo | Calcolo |
| --- | --- |
| `waitingPosition` | Solo se `WAITING`: iscrizioni attive in attesa dello stesso evento con `arrivalOrder` minore, + 1 (FR-015) |
| `availableActions` | Azioni che chi chiama può fare ora: `WITHDRAW`, `APPROVE_UNIT_LEADER`, `REJECT_UNIT_LEADER`, `APPROVE_GROUP_LEADER`, `REJECT_GROUP_LEADER`, `SATISFY` (FR-021) |
