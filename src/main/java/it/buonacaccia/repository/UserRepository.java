package it.buonacaccia.repository;

import java.util.List;
import java.util.Optional;

import it.buonacaccia.model.MemberType;
import it.buonacaccia.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByAgesciUUID(String agesciUUID);

    /** Socio dal codice di censimento (iscrizione fatta dal capo, feature 003). */
    Optional<User> findByCensusMemberCode(String memberCode);

    /** Capi con servizio nell'unità (autorizzazione del capo unità, feature 004). */
    List<User> findByCensusUnitIdAndCensusMemberType(String unitId, MemberType memberType);

    /** Capi gruppo del gruppo (autorizzazione del capo gruppo, feature 004). */
    List<User> findByCensusGroupIdAndCensusGroupLeaderTrue(String groupId);
}
