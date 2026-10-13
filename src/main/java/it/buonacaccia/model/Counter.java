package it.buonacaccia.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Progressivo dei codici evento per regione e anno, es. {@code PIE-2027} (research R5). */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
@Document("counters")
public class Counter {

    @Id
    private String id;

    private long seq;
}
