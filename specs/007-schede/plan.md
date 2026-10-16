# Implementation Plan: Scheda elettronica

**Branch**: `007-schede` | **Date**: 2026-10-15 | **Spec**: [spec.md](spec.md)

## Summary

Modello di scheda per tipo evento (`form_templates`), scheda per iscrizione (`form_submissions`) con copia del modello e risposte, servizio `FormService` con scritture atomiche sull'iscrizione, endpoint per partecipante e segreteria, e-mail di riapertura dal percorso outbox-Kafka della fase 4.

## Technical Context

Stessa base delle fasi 001-006 (Java 21, Spring Boot 4.1.1, MongoDB replica set, Lombok, MapStruct, `@SecurityAccess`, `EventAccessPolicy`, `TransactionRunner`, envelope, Testcontainers). Nessuna dipendenza nuova.

| Metodo | Percorso | Chi |
| --- | --- | --- |
| GET | `/api/v1/event-types/{code}/form-template` | utente autenticato |
| PUT | `/api/v1/event-types/{code}/form-template` | amministrazione (`tipoevento:gestione`) |
| GET | `/api/v1/enrollments/{id}/form` | partecipante, segreteria dell'evento |
| PUT | `/api/v1/enrollments/{id}/form` (`{answers}`) | partecipante |
| POST | `/api/v1/enrollments/{id}/form/submit` | partecipante |
| POST | `/api/v1/enrollments/{id}/form/reopen` (`{reason}`) | segreteria dell'evento (`iscrizione:gestione`) |

Errori: `42200`, `40970` (dominio `FORM`), `40910`, `40300`, `40400`.

## Constitution Check

Spec prima del codice, test prima dell'implementazione, controller snelli, nessun codice inutilizzato. Nessuna violazione.

## Project Structure

```text
src/main/java/it/buonacaccia/
├── model/        FormTemplate (+ FormSection, FormQuestion, QuestionType), FormSubmission, FormStatus
├── repository/   FormTemplateRepository, FormSubmissionRepository
├── service/      FormTemplateService, FormService, FormRules
├── notification/ evento e modello FORM_REOPENED
└── v1/           FormTemplateController, FormController, DTO e mapper
```
