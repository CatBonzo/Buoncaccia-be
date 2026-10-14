package it.buonacaccia.repository;

import java.util.List;

import it.buonacaccia.model.EventCategory;
import it.buonacaccia.model.EventType;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EventTypeRepository extends MongoRepository<EventType, String> {

    List<EventType> findByActiveTrueOrderByCode();

    List<EventType> findAllByOrderByCode();

    List<EventType> findByCategory(EventCategory category);
}
