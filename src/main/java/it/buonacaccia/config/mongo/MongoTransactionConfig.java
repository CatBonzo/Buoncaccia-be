package it.buonacaccia.config.mongo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transazioni MongoDB (replica set della fase 0), usate solo per scrivere un cambio di stato e il suo
 * evento di dominio nell'outbox insieme (research R2 della 004). Le operazioni sulla capienza restano
 * fuori, atomiche con findAndModify come nella fase 3.
 */
@Configuration
public class MongoTransactionConfig {

    @Bean
    MongoTransactionManager transactionManager(MongoDatabaseFactory databaseFactory) {
        return new MongoTransactionManager(databaseFactory);
    }

    @Bean
    TransactionTemplate transactionTemplate(MongoTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
