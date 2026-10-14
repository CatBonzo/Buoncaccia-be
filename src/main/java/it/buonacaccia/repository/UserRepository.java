package it.buonacaccia.repository;

import java.util.Optional;

import it.buonacaccia.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByAgesciUUID(String agesciUUID);

    /** Socio dal codice di censimento (iscrizione fatta dal capo, feature 003). */
    Optional<User> findByCensusMemberCode(String memberCode);
}
