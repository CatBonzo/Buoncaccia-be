package it.buonacaccia.repository;

import java.util.Optional;

import it.buonacaccia.model.AuthorizationLink;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Link di autorizzazione dei capi (feature 004). */
public interface AuthorizationLinkRepository extends MongoRepository<AuthorizationLink, String> {

    Optional<AuthorizationLink> findByTokenHash(String tokenHash);

    Optional<AuthorizationLink> findByDomainEventIdAndLeaderId(String domainEventId, String leaderId);
}
