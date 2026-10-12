package it.buonacaccia.model;

/** Valori del claim {@code role} dell'MVP (FR-016, confermati in Q15). Il ruolo dice che cosa si vede. */
public final class Roles {

    public static final String AMMINISTRATORE = "agesci:sistema:amministratore";
    public static final String SEGRETERIA = "agesci:regione:segreteria";
    public static final String USER = "agesci:socio:user";

    private Roles() {
    }
}
