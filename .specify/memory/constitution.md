<!--
Sync Impact Report
- Versione: (template) → 1.0.0
- Principi: I Qualità del codice, II Test-first, III Coerenza con i client, IV Semplicità
- Sezioni aggiunte: Vincoli tecnologici, Flusso di lavoro, Governance
- Template da rivedere: nessuno (plan, spec e tasks usano i riferimenti generici al "Constitution Check")
- Fonte: Note Tecniche Backend v2.0, par. 1.6
-->

# Costituzione di Buona Caccia Backend

## Principi fondamentali

### I. La qualità del codice è un criterio di rilascio

Controller snelli nel package `api`; la logica di business sta nel package `service`; repository dichiarativi in `repository.nosql`; confini espliciti tra DTO e modello di dominio, con le conversioni nel package `mapper`. Nessun codice inutilizzato e nessun workaround non documentato.

### II. I test definiscono il "completato" (NON NEGOZIABILE)

Ogni modifica di comportamento è coperta da test automatici, scritti prima del codice: devono fallire e poi passare. Una modifica non è completata finché i test che la descrivono non passano e `./gradlew build` non è verde.

### III. Coerenza dell'integrazione con i client

Contratti API stabili e versionati (`/api/v1`), descritti in OpenAPI 3.1 prima dell'implementazione. Validazione uniforme degli input. Ogni risposta usa lo stesso envelope (`errorCode`, `errorMessage`, `errorDomain`, `payload`). La visibilità dei dati si basa su ruolo, entitlement del token e relazione con la risorsa. L'impatto sulla retrocompatibilità è sempre documentato.

### IV. Semplicità delle decisioni tecniche

Si parte dall'opzione più semplice che soddisfa la specifica. Complessità, compromessi ed eccezioni sono documentati nel registro delle scelte di `AGENTS.md` (o in un ADR) con motivazione e impatto.

**Ordine di priorità in caso di conflitto**: (1) correttezza e verificabilità, (2) coerenza dal punto di vista dell'utente e dei client, (3) praticità di implementazione.

## Vincoli tecnologici

- Java 21, Spring Boot 4.0, Gradle (`build.gradle` in Groovy); versioni solo in `gradle/libs.versions.toml`.
- Un'unica applicazione Spring Boot con la struttura a package descritta in `AGENTS.md`.
- MongoDB come database; Apache Kafka per gli eventi di dominio e le notifiche; Docker Compose per l'ambiente locale.
- Autenticazione con token JWT Bearer: prima token mock (profilo `mocked`), poi Keycloak, con gli stessi claim. I permessi sono gli entitlement del token: il backend non ha un catalogo di permessi suo.
- Uso solo locale: nessun servizio esposto fuori da `127.0.0.1`, nessun segreto nel repository.

## Flusso di lavoro

- Ogni feature segue il ciclo Spec-Kit: `specify` → `clarify` → `plan` → `tasks` → `implement`, con `analyze` prima dell'implementazione.
- Specifiche e documentazione in italiano; nomi nel codice in inglese.
- Commit nel formato `verbo: sintesi`, con il riferimento al requisito (FR, BR, US) quando esiste.
- A ogni passo si aggiornano `AGENTS.md` (stato e registro delle scelte) e `docs/tesi/diario-capitolo-3.txt`.

## Governance

Questa costituzione prevale sulle consuetudini locali e sulle impostazioni predefinite dei modelli. Ogni piano, specifica, elenco di attività e revisione ne verifica la conformità ("Constitution Check"). Le modifiche seguono il versionamento MAJOR (principio tolto o ridefinito), MINOR (principio o sezione aggiunti), PATCH (chiarimenti), e vanno registrate in `AGENTS.md`. Le indicazioni operative quotidiane sono in `AGENTS.md`.

**Versione**: 1.0.0 | **Ratificata**: 2026-10-11 | **Ultima modifica**: 2026-10-11
