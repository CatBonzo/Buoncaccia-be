package it.buonacaccia.repository;

import it.buonacaccia.model.RegionSettings;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Coordinate di pagamento per regione (feature 005). */
public interface RegionSettingsRepository extends MongoRepository<RegionSettings, String> {
}
