package it.buonacaccia.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ora corrente dell'applicazione come bean, così i test la possono spostare (research R1).
 * Le regole di dominio sui giorni (BR-03) ragionano nell'ora italiana, {@link #ROME}.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    public static final ZoneId ROME = ZoneId.of("Europe/Rome");

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
