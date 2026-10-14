package it.buonacaccia.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Regioni AGESCI, lista fissa (FR-022, research R3). La sigla è la stessa usata in
 * {@code isMemberOf} della segreteria ({@code agesci:PIE:segreteria}).
 */
public enum Region {

    ABR("Abruzzo"),
    BAS("Basilicata"),
    CAL("Calabria"),
    CAM("Campania"),
    EMR("Emilia-Romagna"),
    FVG("Friuli-Venezia Giulia"),
    LAZ("Lazio"),
    LIG("Liguria"),
    LOM("Lombardia"),
    MAR("Marche"),
    MOL("Molise"),
    PIE("Piemonte"),
    PUG("Puglia"),
    SAR("Sardegna"),
    SIC("Sicilia"),
    TOS("Toscana"),
    TAA("Trentino-Alto Adige"),
    UMB("Umbria"),
    VDA("Valle d'Aosta"),
    VEN("Veneto");

    private final String label;

    Region(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Regione con la sigla indicata, se esiste. */
    public static Optional<Region> fromCode(String code) {
        return Arrays.stream(values()).filter(region -> region.name().equals(code)).findFirst();
    }
}
