package it.buonacaccia.repository;

import it.buonacaccia.model.FormTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Modelli di scheda per tipo evento (feature 007). */
public interface FormTemplateRepository extends MongoRepository<FormTemplate, String> {
}
