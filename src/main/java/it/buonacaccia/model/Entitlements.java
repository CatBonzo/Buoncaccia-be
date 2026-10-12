package it.buonacaccia.model;

/** Valori del claim {@code entitlement} dell'MVP (FR-016, confermati in Q15). L'entitlement dice che cosa si può fare. */
public final class Entitlements {

    private static final String PREFIX = "agesci:entitlement:";

    public static final String TIPOEVENTO_GESTIONE = PREFIX + "tipoevento:gestione";
    public static final String EVENTO_GESTIONE = PREFIX + "evento:gestione";
    public static final String ISCRIZIONE_GESTIONE = PREFIX + "iscrizione:gestione";
    public static final String PAGAMENTO_REGISTRAZIONE = PREFIX + "pagamento:registrazione";
    public static final String ISCRIZIONE_SELF = PREFIX + "iscrizione:self";

    private Entitlements() {
    }
}
