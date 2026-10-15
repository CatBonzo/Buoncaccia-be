# Implementation Plan: Documenti richiesti, caricamento e verifica

**Branch**: `006-documenti` | **Date**: 2026-10-15 | **Spec**: [spec.md](spec.md)

## Summary

L'adempimento documento dell'evento diventa una descrizione strutturata (codice, online o cartaceo, formati, dimensione). Il socio carica un file per adempimento: il controllo guarda i primi byte, il file va nell'archivio (GridFS dietro `DocumentStorage`), il documento è registrato con i suoi metadati e l'adempimento passa `IN_REVIEW`. La segreteria che gestisce l'evento valida o rifiuta con aggiornamenti condizionati sullo stato; il rifiuto riporta l'adempimento `TODO` e manda un'e-mail dal percorso outbox-Kafka della fase 4. Scadenza e promemoria ignorano le iscrizioni con soli adempimenti in verifica.

## Technical Context

Stessa base delle fasi 001-005: Java 21, Spring Boot 4.1.1, MongoDB 8 con replica set, Lombok, MapStruct, `@SecurityAccess`, `EventAccessPolicy`, envelope, circuit breaker, `TransactionRunner`, Clock iniettabile, Testcontainers. Nessuna dipendenza nuova: GridFS è in `spring-data-mongodb` (`GridFsTemplate`), il tipo del file si riconosce dai primi byte.

**Nuovi endpoint** (data model e contratto stanno qui, la feature è piccola):

| Metodo | Percorso | Chi |
| --- | --- | --- |
| POST (multipart, parte `file`) | `/api/v1/enrollments/{id}/obligations/{code}/document` | partecipante o presentatore |
| GET | `/api/v1/enrollments/{id}/documents` | partecipante, presentatore, segreteria dell'evento |
| GET | `/api/v1/documents/{id}/content` | come sopra |
| POST | `/api/v1/documents/{id}/validate` | segreteria dell'evento (`iscrizione:gestione`) |
| POST | `/api/v1/documents/{id}/reject` (`{reason}`) | idem |
| GET | `/api/v1/documents?status=&eventId=&page=&size=` | segreteria (coda) |
| POST | `/api/v1/enrollments/{id}/obligations/DOCUMENT/satisfy?code=` | esistente, guadagna `code` |

**Modello** (collezione `documents`): `id`, `enrollmentId`, `enrollmentNumber`, `obligationCode`, `eventId`, `eventCode`, `eventLevel`, `eventRegion`, `participantId`, `fileName`, `mimeType`, `sizeBytes`, `sha256`, `storageId`, `status` (`UPLOADED`, `VALIDATED`, `REJECTED`, `REPLACED`), `uploadedBy`, `uploadedAt`, `reviewedBy`, `reviewedAt`, `rejectReason`. Indice `{enrollmentId, obligationCode, status}` e `{status, uploadedAt}`.

`Obligation` guadagna `code`, `delivery` (`ONLINE`/`PAPER`), `allowedMimeTypes`, `maxSizeBytes`; `EnrollmentObligation` gli stessi più il codice; `ObligationStatus` guadagna `IN_REVIEW`.

**Errori**: `42200` (campo `file`), `40960` (dominio `DOCUMENT`, azione non ammessa sullo stato del documento), `40910`, `40300`, `40400`.

## Constitution Check

Specifica prima del codice (spec, research, tasks scritti prima), test prima dell'implementazione, controller snelli e logica nei servizi, nessun codice inutilizzato. Nessuna violazione.

## Project Structure

```text
src/main/java/it/buonacaccia/
├── model/        Document, DocumentStatus, DocumentDelivery; Obligation/EnrollmentObligation/ObligationStatus estesi
├── repository/   DocumentRepository
├── service/      DocumentService, FileTypeDetector, DocumentStorage (+ GridFsDocumentStorage), DocumentRules
├── notification/ nuovo evento DOCUMENT_REJECTED e modello DOCUMENT_REJECTED
└── v1/           DocumentController, DocumentDTO, DocumentPageDTO, DocumentMapper
```
