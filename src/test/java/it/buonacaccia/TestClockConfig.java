package it.buonacaccia;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Sostituisce il {@code Clock} dell'applicazione con un {@link MutableClock} in tutti i test di integrazione. */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfig {

    @Bean
    @Primary
    MutableClock mutableClock() {
        return new MutableClock();
    }
}
