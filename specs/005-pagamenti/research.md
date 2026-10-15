# Research: Pagamenti e rimborsi

Decisioni tecniche della feature 005. Formato: Decisione, Perché, Alternative scartate.

## R1. Importi in centesimi interi

- **Decisione**: `Payment.amountCents`, `Refund.amountCents`, `Enrollment.feeCents` e `paidCents` sono `long`. Le API accettano e restituiscono euro con due decimali (`BigDecimal`), convertiti da `Money` (`toCents` rifiuta più di due decimali, `toEuro` restituisce scala 2).
- **Perché**: Spring Data MongoDB salva i `BigDecimal` come stringhe, quindi non si sommano né si confrontano nel database; con i centesimi il versato è una somma di interi e il controllo "non oltre la quota" un confronto. Nessun errore di arrotondamento.
- **Alternative**: `Decimal128` (serve un convertitore e aggregazioni più complesse per un uso minimo); `double` (errori di arrotondamento sui soldi).

## R2. Il versato sta nell'iscrizione, protetto da `@Version`

- **Decisione**: `Enrollment.paidCents` è la somma dei pagamenti validi, aggiornata solo da `PaymentService` nella stessa transazione che inserisce o annulla il pagamento. L'iscrizione ha già `@Version`: due registrazioni contemporanee sulla stessa iscrizione non possono entrambe riuscire; la seconda fallisce con `OptimisticLockingFailureException` (409 `40903`). Un conflitto di scrittura fra transazioni di MongoDB (`TransientTransactionError`) si riprova una volta.
- **Perché**: il vincolo "totale ≤ quota" non si può esprimere con un indice; con un solo campo versionato resta vero anche sotto concorrenza, senza lock.
- **Alternative**: calcolare il versato a ogni lettura sommando i pagamenti (non protegge la scrittura); `findAndModify` con `$expr` (funziona, ma sposta fuori dalla transazione adempimento e completamento).

## R3. Quota dovuta fissata all'iscrizione

- **Decisione**: `Enrollment.feeCents` si imposta all'iscrizione dalla quota dell'evento; per le iscrizioni più vecchie, senza il campo, vale la quota dell'evento. Il residuo è `feeCents − paidCents`.
- **Perché**: edge case della spec (la quota cambia dopo un pagamento); chi ha pagato la quota di allora non deve diventare debitore.
- **Alternative**: leggere sempre la quota dell'evento (il residuo cambierebbe sotto i piedi).

## R4. Registrare un pagamento: stesso quadro degli adempimenti della fase 3

- **Decisione**: `PaymentService.register` carica l'iscrizione, controlla permessi (`EventAccessPolicy.checkCanManage` sul livello e la regione dell'iscrizione, come `satisfy`), stato (attiva, nei posti, con adempimento `PAYMENT` da fare: altrimenti 409 `40910`), importo (> 0, ≤ residuo: 422 `42200` con `FieldErrorDTO`) e doppione (stesso importo e riferimento non vuoto fra i pagamenti validi: 409 `40930`). Poi in transazione: inserisce il pagamento, aggiorna `paidCents`, se `paidCents == feeCents` soddisfa l'adempimento (`satisfiedBy` = chi registra) e chiama `EnrollmentService.completeIfDone`, e scrive `PAYMENT_REGISTERED` (e `ENROLLMENT_COMPLETED` se l'iscrizione si completa).
- **Perché**: riusa le stesse regole e gli stessi eventi della fase 3; l'adempimento resta una sola cosa, soddisfatta da un solo punto del codice.
- **Alternative**: far passare ogni pagamento da `EnrollmentService.satisfy` (non conosce importi né rate).
- **Nota**: `satisfy` della fase 3 sul tipo `PAYMENT` resta come forzatura manuale ("pagamento segnato senza importo"): non crea pagamenti e quindi non genera rimborsi.

## R5. Annullare un pagamento

- **Decisione**: `POST /payments/{id}/cancel` con motivo obbligatorio: il pagamento passa a `CANCELLED` (non si cancella mai un documento), `paidCents` scende, e se la quota non è più coperta su un'iscrizione attiva l'adempimento torna `TODO`; un'iscrizione `COMPLETED` torna `REGISTERED` con una scadenza nuova di 9 giorni. Su un'iscrizione chiusa l'annullamento è permesso e basta ricalcolare il versato, ma se un rimborso è già nato il pagamento non si annulla (409 `40910`).
- **Perché**: senza storno la segreteria che sbaglia l'importo non ha rimedio; la scadenza nuova evita di far scadere un socio per un errore della segreteria.
- **Alternative**: nessun annullamento (spec richiede FR-006).

## R6. Il rimborso nasce nella transazione di chiusura

- **Decisione**: `EnrollmentService.exit` e `cancelAll` salvano l'iscrizione chiusa e, nella stessa transazione, chiamano `RefundService.requestFor(enrollment)`: se `paidCents > 0` e il diritto c'è (ritiro: `refundDue` di BR-10; scadenza e diniego: sì; evento annullato: sì), inserisce un `Refund` `REQUESTED` di importo `paidCents` e scrive `REFUND_REQUESTED`. Un indice unico su `enrollmentId` impedisce un secondo rimborso. Per scadenza e diniego `Enrollment.refundDue` diventa `paidCents > 0`.
- **Perché**: chiusura e rimborso sono atomici (niente iscrizioni chiuse con diritto ma senza richiesta); nessun job nuovo.
- **Alternative**: un job che cerca le iscrizioni chiuse senza rimborso (può lasciare un buco); evento di dominio che il consumer trasforma in rimborso (il rimborso è un fatto del dominio, non una notifica).
- **Nota**: il flag `refundDue` della fase 3 resta il diritto; il rimborso c'è solo se qualcosa è stato versato. Il default per scadenza parziale e diniego è quello delle Clarifications.

## R7. Stati del rimborso

- **Decisione**: transizioni ammesse `REQUESTED → IN_PROGRESS`, `REQUESTED/IN_PROGRESS → ISSUED`, `REQUESTED/IN_PROGRESS → REJECTED`; ogni altra: 409 `40940`. Ogni transizione è un `findAndModify` condizionato sullo stato di partenza (due segreterie insieme: vince la prima). `ISSUED` richiede `issuedOn` (non nel futuro) e riferimento facoltativo; `REJECTED` richiede il motivo.
- **Perché**: FR-010, SC-005; l'aggiornamento condizionato dà l'esclusione senza lock.
- **Alternative**: `@Version` sul rimborso (stesso effetto, ma il conflitto sarebbe un 409 generico invece del 40940).

## R8. Eventi di dominio e e-mail

- **Decisione**: nuovi `DomainEventType`: `PAYMENT_REGISTERED`, `REFUND_REQUESTED`, `REFUND_ISSUED`, `REFUND_REJECTED`; nuovi modelli `PAYMENT_RECEIVED`, `REFUND_REQUESTED`, `REFUND_ISSUED`, `REFUND_REJECTED`. `DomainEvent` guadagna `MoneySnapshot(amount, paid, due, date, reference, reason)` (null per gli eventi vecchi). Destinatari: partecipante e presentatore, come i cambi di stato della fase 4. La cancellazione di un pagamento non manda e-mail.
- **Perché**: stesso percorso della fase 4, già provato (outbox, Kafka, idempotenza). Un messaggio già in outbox senza il campo nuovo si legge ancora (campo assente = null).
- **Alternative**: e-mail anche per `IN_PROGRESS` (rumore: il socio non deve fare nulla).

## R9. Visibilità del pagamento nell'iscrizione

- **Decisione**: `EnrollmentDTO.payment` (`fee`, `paid`, `due`) per ogni iscrizione con quota > 0; `paymentInfo` (intestatario, IBAN, contatti, orari della regione dell'evento o della nazionale) solo se esiste un adempimento `PAYMENT` da fare; `refund` (stato, importo) se esiste. Chi vede l'iscrizione vede questi dati (stessa regola di `canView`).
- **Perché**: US1 senza una chiamata in più; gli IBAN delle segreterie non sono mostrati a chi non deve pagare.

## R10. Coordinate della regione

- **Decisione**: collezione `region_settings` con `_id` = sigla della regione o `NAT`; `GET/PUT /api/v1/region-settings/{key}`. Chi modifica: amministrazione per tutte, segreteria per la propria (`EventAccessPolicy.canManage` con livello regionale), `NAT` solo l'amministrazione; entitlement `pagamento:registrazione`. IBAN validato da `Iban` (lunghezza per paese e modulo 97) e salvato senza spazi in maiuscolo. Nessuna impostazione = nessun IBAN (US1.4).
- **Perché**: FR-016…FR-018; una pagina di configurazione sola.
- **Alternative**: file di configurazione (non modificabile dalla segreteria).

## R11. Riconciliazione e CSV

- **Decisione**: `GET /events/{id}/reconciliation` calcola i totali dai pagamenti e dai rimborsi dell'evento (incassato = pagamenti `VALID`, annullato = `CANCELLED`, da rimborsare = rimborsi `REQUESTED`+`IN_PROGRESS`, rimborsato = `ISSUED`) e l'elenco delle iscrizioni attive con residuo > 0 (`feeCents − paidCents` con adempimento `PAYMENT` da fare). `GET /events/{id}/reconciliation/export` restituisce `text/csv` UTF-8 con `;` come separatore (Excel italiano), una riga per pagamento e per rimborso, campi fra virgolette se contengono `;`, `"` o a capo.
- **Perché**: FR-014, FR-015; il CSV scritto a mano evita una libreria per quattro colonne di testo.
- **Alternative**: aggregazione MongoDB (gli importi sono interi, ma il numero di righe per evento è piccolo).

## R12. Errori

- **Decisione**: nuovo dominio `PAYMENT`: `40930` (pagamento doppio), `40931` (pagamento già annullato); dominio `REFUND`: `40940` (transizione non ammessa). Importo e IBAN non validi: `42200` con `FieldErrorDTO`. Risorsa inesistente o fuori regione per i rimborsi: `40400`.
- **Perché**: FR-019; stesso schema `SSSNN` delle fasi precedenti.

## R13. Test

- **Decisione**: gli stessi `AbstractTest` e `MailpitClient` della fase 4 (Mongo con replica set, Kafka, Mailpit); le e-mail si leggono per oggetto (numero iscrizione). Prima di ogni test si cancellano anche `payments`, `refunds` e `region_settings` toccati.
- **Perché**: le conferme per e-mail fanno parte dei criteri di accettazione.
