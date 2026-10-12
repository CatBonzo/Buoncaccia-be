package it.buonacaccia.repository.nosql;

import java.util.Optional;

import it.buonacaccia.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByAgesciUUID(String agesciUUID);
}
