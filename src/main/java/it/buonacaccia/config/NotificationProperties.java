package it.buonacaccia.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Proprietà delle notifiche e-mail (feature 004, {@code buonacaccia.notifications} in application.yml).
 *
 * @param from        mittente delle e-mail
 * @param linkBaseUrl pagina del frontend che riceve il token del link di autorizzazione (research R8)
 * @param relayDelay  pausa tra due giri del relay outbox → Kafka (research R1)
 * @param retry       ritentativi dell'invio prima del dead letter (research R5)
 */
@ConfigurationProperties("buonacaccia.notifications")
public record NotificationProperties(String from, String linkBaseUrl, Duration relayDelay, Retry retry) {

    public record Retry(Duration initialInterval, double multiplier, int maxAttempts) {
    }
}
