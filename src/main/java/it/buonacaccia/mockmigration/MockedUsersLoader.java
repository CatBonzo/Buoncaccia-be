package it.buonacaccia.mockmigration;

import it.buonacaccia.config.MockedIdentityProperties;
import it.buonacaccia.service.MockIdentityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** All'avvio con il profilo {@code mocked} carica gli utenti di {@code mocked-identity.yml} ed emette i token. */
@Component
@Profile("mocked")
public class MockedUsersLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MockedUsersLoader.class);

    private final MockedIdentityProperties properties;
    private final MockIdentityService mockIdentityService;

    public MockedUsersLoader(MockedIdentityProperties properties, MockIdentityService mockIdentityService) {
        this.properties = properties;
        this.mockIdentityService = mockIdentityService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int loaded = mockIdentityService.load(properties.users()).size();
        log.info("Utenti di prova caricati: {}. Token su GET /api/v1/mock/auth/findAll", loaded);
    }
}
