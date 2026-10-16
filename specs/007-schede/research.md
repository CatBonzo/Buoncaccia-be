# Research: Scheda elettronica

## R1. Dove sta il modello
- **Decisione**: collezione `form_templates`, una per tipo evento (id = codice del tipo), sezioni e domande dentro il documento.
- **Perché**: FR-050; non tocca `EventType` e si legge con una chiamata.
- **Alternative**: campo dentro `EventType` (mescola due responsabilità); un modello per evento (nessun riuso).

## R2. Copia del modello nella scheda
- **Decisione**: la scheda (`form_submissions`, indice unico su `enrollmentId`) salva la copia delle sezioni al primo salvataggio.
- **Perché**: FR-004; chi ha cominciato non vede le domande cambiare.

## R3. Risposte
- **Decisione**: mappa `questionId → valore stringa`. Validazione per tipo: `YES_NO` = `true`/`false`, `CHOICE` = una delle opzioni, `DATE` = ISO `yyyy-MM-dd`, testi ≤ 2000. Risposta vuota = domanda non risposta (si toglie dalla mappa).
- **Perché**: un solo formato semplice; il tipo vero lo stabilisce il modello.

## R4. Stati e concorrenza
- **Decisione**: `DRAFT → SUBMITTED → DRAFT` (riapertura). Ogni scrittura rilegge iscrizione e scheda, modifica entrambe nella stessa transazione e salva l'iscrizione con la sua `@Version` (un conflitto riprova una volta, come per i documenti e i pagamenti).
- **Perché**: SC-003, stesso schema delle fasi 5 e 6.

## R5. Adempimento
- **Decisione**: `ELECTRONIC_FORM` resta `TODO` finché non si invia, poi `SATISFIED` (nessuno stato intermedio: non c'è verifica). La riapertura lo riporta `TODO` con la nota e allunga la scadenza ad almeno 3 giorni.
- **Perché**: FR-005, FR-007; stessa regola dei documenti rifiutati.

## R6. Modello obbligatorio per l'evento
- **Decisione**: `EventService` controlla alla creazione e alla modifica in bozza che un evento con adempimento `ELECTRONIC_FORM` abbia un modello per il suo tipo.
- **Perché**: FR-002; meglio un 422 alla segreteria che una scheda impossibile da compilare.

## R7. Riservatezza
- **Decisione**: nessuna risposta negli eventi di dominio né nei log; l'e-mail di riapertura riporta solo il motivo. Visibilità: `isParticipant` (non il presentatore) o `canManage`.
- **Perché**: FR-008, FR-010; la matrice dei permessi esclude il presentatore.

## R8. E-mail
- **Decisione**: nuovo `DomainEventType.FORM_REOPENED` e modello `FORM_REOPENED` per il solo partecipante; il completamento riusa `ENROLLMENT_COMPLETED`.
- **Perché**: il partecipante deve sapere cosa rifare; nessuna e-mail per salvataggio e invio.
