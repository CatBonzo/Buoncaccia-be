# Data Model: Identità, ruoli e autenticazione mock

**Feature**: `001-identita-ruoli` | **Data**: 2026-10-12 | **Plan**: [plan.md](plan.md)

## User (collezione `users`)

Documento unico per la persona (spec di progetto §2.4).

| Campo | Tipo | Regole |
| --- | --- | --- |
| `id` | string | Identificativo Buona Caccia (`buonaCacciaUUID`). Dalla voce del file se indicato (es. `user-001`), altrimenti UUID generato alla creazione. Non cambia mai (FR-001, FR-002). |
| `agesciUUID` | string | Obbligatorio. Indice univoco. Chiave dell'upsert e della ricerca dal token. |
| `username` | string | Obbligatorio (claim `preferred_username`). |
| `email` | string | Facoltativo. |
| `firstName` | string | Obbligatorio. |
| `lastName` | string | Obbligatorio. |
| `status` | enum `UserStatus` | `ACTIVE` alla creazione. Nessun'altra transizione in questa feature. |
| `census` | `Census` o `null` | `null` = persona non socia AGESCI (FR-012, AUTH-4). |
| `roles` | lista di string | Copia del claim `role` dell'ultimo token (FR-011). Non usata per autorizzare. |
| `entitlements` | lista di string | Copia del claim `entitlement` dell'ultimo token (FR-011). Non usata per autorizzare. |
| `createdAt`, `updatedAt` | instant (UTC) | Gestiti dall'applicazione. |

Campi della spec di progetto rimandati a feature successive: `dataNascita`, `sesso`, `telefono`.

**Valore derivato**: `member` (boolean) = `census != null`. Esposto nel profilo, non salvato.

## Census (sotto-documento di User)

| Campo | Tipo | Regole |
| --- | --- | --- |
| `memberCode` | string | Codice socio. Obbligatorio se `census` è presente. Indice univoco sparse (`census.memberCode`). |
| `groupId` | string | Facoltativo in questa feature (le strutture arrivano con la feature 002). |
| `unitId` | string | Facoltativo. |
| `branch` | enum `Branch` | `LC`, `EG`, `RS`, `CO` (comunità capi). |
| `memberType` | enum `MemberType` | `YOUTH` (ragazzo) o `LEADER` (capo). |
| `status` | enum `CensusStatus` | `IN_GOOD_STANDING` o `NOT_IN_GOOD_STANDING`. |
| `year` | int | Anno di censimento. |
| `lastSyncAt` | instant o `null` | Ultima sincronizzazione con il censimento; `null` per i dati del file di prova. |

## MockedUser (voce di `mocked-identity.yml`, solo profilo `mocked`)

Non è un documento: è configurazione letta all'avvio.

| Campo | Regole |
| --- | --- |
| `agesciUUID` | Obbligatorio, unico nel file (altrimenti l'avvio fallisce). |
| `buonaCacciaUUID` | Facoltativo; se presente diventa l'`id` dell'utente. |
| `username`, `label`, `firstName`, `lastName` | Obbligatori. `label` serve solo all'elenco per il frontend. |
| `email` | Facoltativo. |
| `authentication.isMemberOf` | Obbligatorio (es. `agesci:socio:true`). |
| `authentication.role` | Lista, almeno un valore. |
| `authentication.entitlement` | Lista, anche vuota. |
| `census` | Facoltativo, stessi campi di `Census` tranne `lastSyncAt`. |

### Utenti di prova previsti

| `buonaCacciaUUID` | username | Ruolo | Entitlement (`agesci:entitlement:` +) | Censimento |
| --- | --- | --- | --- | --- |
| `user-001` | `amministratore` | `agesci:sistema:amministratore` | tutti e cinque | no |
| `user-002` | `segreteria` | `agesci:regione:segreteria` | `evento:gestione`, `iscrizione:gestione`, `pagamento:registrazione` | no |
| `user-003` | `socio` | `agesci:socio:user` | `iscrizione:self` | sì, in regola (capo, `CO`) |
| `user-004` | `esterno` | `agesci:socio:user` | `iscrizione:self` | no (`isMemberOf` = `agesci:socio:false`) |

## Token di accesso (solo in memoria)

JWT RS256. Claim: `iss` (`mock-auth`), `sub` (= `buonaCacciaUUID`), `iat`, `exp`, `agesciUUID`, `isMemberOf`, `role[]`, `entitlement[]`, `preferred_username`, `email`, `given_name`, `family_name`. Vale finché il processo non si riavvia (la chiave di firma cambia a ogni avvio).

## Envelope di risposta

`ApiResponse<T>`: `errorCode` (int, 0 = successo), `errorMessage` (string, italiano), `errorDomain` (`AUTH`, `IDENTITY`, `SYSTEM`; `null` in caso di successo), `payload` (T o `null` in caso di errore).

| Codice | Status HTTP | Dominio | Quando |
| --- | --- | --- | --- |
| 0 | 200 / 201 | – | Successo |
| 40100 | 401 | `AUTH` | Token assente, malformato, firma o emittente non validi, scaduto |
| 40300 | 403 | `AUTH` | Entitlement richiesto assente dal token |
| 40400 | 404 | `SYSTEM` | Risorsa o percorso inesistente (anche `/api/v1/mock/**` fuori dal profilo `mocked`) |
| 40500 | 405 | `SYSTEM` | Metodo HTTP non supportato dalla risorsa |
| 40900 | 409 | dominio della risorsa | Conflitto di stato (non usato in questa feature, riservato) |
| 42200 | 422 | `SYSTEM` | Input non valido |
| 50000 | 500 | `SYSTEM` | Errore imprevisto; messaggio generico, nessun dettaglio interno |
| 50300 | 503 | `SYSTEM` | MongoDB non raggiungibile o circuit breaker aperto |
