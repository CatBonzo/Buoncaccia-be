# Research: Documenti richiesti, caricamento e verifica

## R1. Archivio dei file

- **Decisione**: interfaccia `DocumentStorage` (`store`, `open`, `delete`) con un'unica implementazione su GridFS (`GridFsTemplate`) nel database già in uso.
- **Perché**: nessun container in più e nessuna dipendenza; i file sono piccoli (≤ 10 MB). La spec di progetto prevede S3/MinIO: l'interfaccia lascia la porta aperta.
- **Alternative**: MinIO con client S3 (container, credenziali e dipendenza per file di pochi MB); filesystem (non sopravvive al container).
- **Nota**: GridFS non partecipa alla transazione: si scrive il file prima, poi in transazione si registra il documento; se la transazione fallisce il file orfano si cancella al meglio.

## R2. Riconoscere il tipo reale

- **Decisione**: `FileTypeDetector` guarda i primi byte: JPEG `FF D8 FF`, PNG `89 50 4E 47 0D 0A 1A 0A`, PDF `%PDF-`. Il tipo rilevato deve essere fra quelli ammessi dall'adempimento; il tipo dichiarato e l'estensione si ignorano.
- **Perché**: FR-005; senza antivirus è il controllo minimo contro un eseguibile con estensione `.jpg`.
- **Alternative**: Apache Tika (decine di MB di dipendenza per tre formati).

## R3. Limiti di dimensione

- **Decisione**: limite multipart a 10 MB in `application.yml`; il controllo per adempimento (`maxSizeBytes`, default 5 MB) lo fa il servizio. Un file oltre il limite del server risponde 422 sul campo `file`.
- **Perché**: un solo messaggio d'errore per il client, qualunque sia il livello che scatta.

## R4. Un adempimento per documento

- **Decisione**: `ObligationPlanner.initial` non deduplica più per tipo ma per tipo e codice; un documento senza codice prende `DOC` (compatibilità con gli eventi della fase 2). `satisfy` accetta `?code=` per segnare ricevuto un cartaceo preciso; senza codice resta il primo da fare.
- **Perché**: FR-002; la dedupe per tipo avrebbe fuso foto e liberatoria in un solo adempimento.

## R5. Stato "in verifica"

- **Decisione**: `ObligationStatus.IN_REVIEW`. `statusFor` considera aperto sia `TODO` sia `IN_REVIEW` (l'iscrizione è `COMPLETED` solo con tutto soddisfatto). Scadenza e promemoria guardano se resta almeno un `TODO`.
- **Perché**: FR-012, SC-004; chi aspetta la verifica non deve scadere.

## R6. Transizioni del documento

- **Decisione**: `UPLOADED → VALIDATED | REJECTED`, `UPLOADED → REPLACED` (nuovo caricamento), con aggiornamento condizionato sullo stato (`findAndModify`) nella stessa transazione dell'iscrizione e dell'eventuale evento di dominio. Un nuovo caricamento prende il vecchio `UPLOADED` o `REJECTED` e lo marca `REPLACED` solo se è ancora in quello stato; `TransactionRunner` ritenta una volta sui conflitti.
- **Perché**: SC-002, FR-010; stesso schema dei rimborsi (fase 5).

## R7. E-mail

- **Decisione**: nuovo `DomainEventType.DOCUMENT_REJECTED` e modello `DOCUMENT_REJECTED` (partecipante e presentatore). Il motivo e il codice del documento viaggiano in un nuovo `DocumentSnapshot(code, description, reason)` dentro il `DomainEvent`. Nessuna e-mail per caricamento e validazione (il completamento ha già la sua).
- **Perché**: SC-005; il socio deve sapere cosa rifare, non essere avvisato di ogni passo.

## R8. Visibilità

- **Decisione**: `canView` ristretto: partecipante, presentatore (`isParticipantOrPresenter`) o chi gestisce l'evento; altrimenti 404 sia per l'elenco sia per il file.
- **Perché**: FR-013; sono foto di minorenni, quindi niente capo unità/gruppo.

## R9. Test

- **Decisione**: file di prova generati nei test (byte iniziali di JPEG/PNG/PDF più riempimento); richieste multipart con `HttpClient`; stesse basi della fase 5 (`EnrollmentTestSupport`, `MailpitClient`).
