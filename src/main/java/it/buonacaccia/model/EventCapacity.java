package it.buonacaccia.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Posti e ordine di arrivo di un evento (collezione {@code event_capacity}, research R1 della 003).
 * Si modifica solo con aggiornamenti atomici condizionati, mai con {@code save}: così l'ordine di
 * arrivo resta unico anche con molte iscrizioni nello stesso istante.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
@Document("event_capacity")
public class EventCapacity {

    /** Id dell'evento. */
    @Id
    private String id;
    private int seats;
    private long lastArrival;
    private int inSeats;
    private int waiting;
    /** Iscrizioni attive ({@code inSeats + waiting}): il numero mostrato nel catalogo. */
    private int active;
}
