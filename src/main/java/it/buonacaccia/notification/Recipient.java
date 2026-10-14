package it.buonacaccia.notification;

import it.buonacaccia.model.Notification;
import it.buonacaccia.model.NotificationTemplate;
import it.buonacaccia.model.RecipientRole;
import it.buonacaccia.model.User;

/**
 * Chi riceve un avviso, con quale modello e per quale iscrizione (research R6 della 004).
 * {@code user} è null per la notifica {@code NO_RECIPIENT}.
 */
public record Recipient(User user, RecipientRole role, NotificationTemplate template, String enrollmentId) {

    public String userId() {
        return user == null ? Notification.NOBODY : user.getId();
    }
}
