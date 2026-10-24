package it.buonacaccia.model;

/**
 * Come è arrivato un pagamento: bonifico registrato dalla segreteria (feature 005) oppure carta pagata dal socio
 * (feature 018, simulata: nessuna banca coinvolta, l'esito è sempre positivo).
 */
public enum PaymentMethod {
    BANK_TRANSFER,
    CARD
}
