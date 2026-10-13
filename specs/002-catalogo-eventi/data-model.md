# Data model: Catalogo eventi

**Feature**: `002-catalogo-eventi` | **Data**: 2026-10-13 | **Plan**: [plan.md](plan.md)

Nomi nel codice e nel JSON in inglese (regola di AGENTS.md); fra parentesi il nome della spec. Documenti come classi Lombok (`@AllArgsConstructor @NoArgsConstructor @Getter @Setter @ToString @Builder`).

## Region (Regione) – enum

Sigla e nome, lista fissa (research R3). Esempi: `PIE` Piemonte, `LIG` Liguria. Usata in `Event.region` e in `isMemberOf` (`agesci:PIE:segreteria`).

## EventType (Tipo evento) – collezione `event_types`

| Campo | Tipo | Regole |
| --- | --- | --- |
| `code` | string, `@Id` | Obbligatorio, `^[A-Z0-9_]{2,20}$`, univoco (FR-001); 409 `40902` se esiste |
| `name` | string | Obbligatorio, max 120 |
| `category` | enum `EventCategory` | Obbligatoria: `LC`, `EG`, `RS`, `LEADER_TRAINING`, `ASSOCIATION` |
| `defaultRequirements` | `Requirements` | Facoltativo |
| `defaultObligations` | lista `Obligation` | Facoltativa |
| `application` | boolean | "a candidatura" (BR-12), default false |
| `active` | boolean | Default true; disattivato = non usabile per nuovi eventi (FR-003) |
| `createdAt`, `updatedAt` | Instant | Gestiti dal sistema |

Non si cancella: si disattiva.

## Event (Evento) – collezione `events`

| Campo | Tipo | Regole (bozza: tutto facoltativo salvo `typeCode`, `level`; alla pubblicazione invarianti FR-007) |
| --- | --- | --- |
| `id` | string (UUID), `@Id` | Generato |
| `code` | string | `<SIGLA>-<ANNO>-<NNN>`, generato alla creazione (R5), univoco |
| `typeCode` | string | Tipo evento attivo alla creazione; alla pubblicazione basta che esista (una bozza resta pubblicabile se il tipo viene disattivato dopo) |
| `title` | string | Obbligatorio per pubblicare, max 200 |
| `description` | string | Max 5000 |
| `level` | enum `EventLevel` | `NATIONAL`, `REGIONAL`, `ZONE` |
| `region` | enum `Region` | Obbligatoria salvo `NATIONAL`; per la segreteria = la sua regione (FR-010) |
| `zone` | string | Obbligatoria se e solo se `level = ZONE`, max 100 |
| `location`, `province` | string | Località obbligatoria per pubblicare; provincia sigla di 2 lettere |
| `startDate`, `endDate` | LocalDate | Partenza ≤ rientro |
| `registrationOpenDate`, `registrationCloseDate` | LocalDate | Apertura < chiusura ≤ partenza |
| `seats` | int | > 0 |
| `fee` | BigDecimal (Decimal128) | ≥ 0, due decimali |
| `enrollmentMode` | enum | `SELF`, `BY_LEADER`, `BOTH` |
| `requirements` | `Requirements` | Default dal tipo (FR-005) |
| `obligations` | lista `Obligation` | Default dal tipo (FR-005) |
| `contacts` | `Contacts` | Metodologico e amministrativo: nome, e-mail, telefono |
| `lifecycle` | enum `EventLifecycle` | `DRAFT`, `PUBLISHED`, `CANCELLED` (stato salvato) |
| `registrationClosedEarlyAt` | Instant | Valorizzato dalla chiusura anticipata (FR-015) |
| `cancellationReason` | string | Obbligatorio per annullare, max 500 |
| `cancelledAt` | Instant | |
| `registrationOpensAt`, `registrationClosesAt`, `startsAt`, `endsAt` | Instant | Calcolati da date + Europe/Rome a ogni salvataggio (R1), interni |
| `enrolledCount` | int | Sempre 0 in questa feature (fase 3 lo aggiornerà) |
| `version` | Long, `@Version` | Concorrenza ottimistica (R4) |
| `createdBy`, `createdAt`, `updatedAt` | string, Instant | `createdBy` = `sub` del token |

**Indici**: `code` univoco; `{lifecycle, startsAt}`; `{region, startsAt}`; `{typeCode, startsAt}`.

### Sotto-documenti

- **Requirements**: `minAge`, `maxAge` (interi, età alla partenza, min ≤ max), `branches` (lista `Branch`: LC, EG, RS, CO), `memberTypes` (lista `MemberType`: YOUTH, LEADER), `oneActivePerType` (boolean), `maxPerGroup` (intero > 0 o vuoto).
- **Obligation**: `type` (`LEADER_VALIDATION`, `UNIT_LEADER_AUTHORIZATION`, `GROUP_LEADER_AUTHORIZATION`, `PAYMENT`, `DOCUMENT`, `ELECTRONIC_FORM`), `description` (es. "foto JPG ≤ 1 MB").
- **Contacts**: `methodological { name, email, phone }`, `administrative { name, email, phone }`.

## Stato effettivo (calcolato, research R1)

`EventStatus` (nome della spec fra parentesi), funzione di `lifecycle`, `registrationClosedEarlyAt`, istanti e ora corrente:

| Condizione (in ordine) | Stato |
| --- | --- |
| `lifecycle = DRAFT` | `DRAFT` (BOZZA) |
| `lifecycle = CANCELLED` | `CANCELLED` (ANNULLATO) |
| ora < `registrationOpensAt` | `PUBLISHED` (PUBBLICATO) |
| ora ≤ `registrationClosesAt` e nessuna chiusura anticipata | `REGISTRATION_OPEN` (ISCRIZIONI_APERTE) |
| ora < `startsAt` | `REGISTRATION_CLOSED` (ISCRIZIONI_CHIUSE) |
| ora ≤ `endsAt` | `IN_PROGRESS` (IN_CORSO) |
| altrimenti | `COMPLETED` (CONCLUSO) |

### Transizioni comandate dalle persone

| Da (stato effettivo) | Azione | A | Chi | Errore se non consentita |
| --- | --- | --- | --- | --- |
| — | crea | `DRAFT` | gestore della regione | 403 regione, 422 dati |
| `DRAFT` | pubblica | stato secondo l'ora | gestore | 422 `42201` invarianti |
| `REGISTRATION_OPEN` | chiudi iscrizioni | `REGISTRATION_CLOSED` | gestore | 409 `40901` |
| da `DRAFT` a `IN_PROGRESS` | annulla con motivo | `CANCELLED` | gestore | 409 `40901`, 422 motivo |
| `DRAFT` | modifica qualsiasi campo | `DRAFT` | gestore | — |
| da `PUBLISHED` a `REGISTRATION_CLOSED` | modifica campi consentiti (FR-016) | invariato | gestore | 409 `40904` campo bloccato, 422 invarianti |
| `IN_PROGRESS`, `COMPLETED`, `CANCELLED` | modifica | — | — | 409 `40901` |

## AvailabilityIndicator (indicatore, BR-14) – calcolato

| Condizione | Valore |
| --- | --- |
| stato `PUBLISHED` | `NOT_YET_OPEN` (DA_APRIRE) |
| stato `REGISTRATION_OPEN` e iscritti < 90% dei posti | `OPEN` (APERTE) |
| stato `REGISTRATION_OPEN`, iscritti tra 90% e 120% | `LIMITED` (LIMITATE) |
| stato `REGISTRATION_OPEN`, iscritti > 120% | `HEAVY_OVERBOOKING` (SOVRANNUMERO_PESANTE) |
| ogni altro stato | `CLOSED` (CHIUSE) |

## Counter – collezione `counters`

`_id` = `<SIGLA>-<ANNO>`, `seq` = ultimo progressivo, aggiornato con `findAndModify` + `$inc` + upsert (R5).

## Rapporti con la feature 001

- Nessuna modifica a `User`/`Census`. `Branch` e `MemberType` riusati.
- Nuovi codici di errore (R7): `40901`, `40902`, `40903`, `40904`, `42201`, dominio `CATALOG`.
