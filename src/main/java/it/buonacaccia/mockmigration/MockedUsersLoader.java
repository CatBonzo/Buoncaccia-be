package it.buonacaccia.mockmigration;

import it.buonacaccia.config.MockedIdentityProperties;
import it.buonacaccia.service.MockIdentityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** All'avvio con il profilo {@code mocked} carica gli utenti di {@code mocked-identity.yml} ed emette i token. */
@Slf4j
@Component
@Profile("mocked")
@RequiredArgsConstructor
public class MockedUsersLoader implements ApplicationRunner {

    private final MockedIdentityProperties properties;
    private final MockIdentityService mockIdentityService;

    @Override
    public void run(ApplicationArguments args) {
        int loaded = mockIdentityService.load(properties.users()).size();
        log.info("Utenti di prova caricati: {}. Token su GET /api/v1/mock/auth/findAll", loaded);
    }
}
