package com.courtside.api.services;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.courtside.api.entities.Booking;
import com.courtside.api.entities.User;
import com.courtside.api.entities.WaitlistEntry;

/**
 * Outbound user notifications.
 *
 * DELIBERATELY A STUB: it logs instead of sending. Wiring a real provider (SMTP,
 * SendGrid, Twilio) is configuration, not design — and doing it now would mean
 * credentials, deliverability and bounce handling for zero learning.
 *
 * What matters is that the rest of the codebase already talks to an ABSTRACTION:
 * every caller depends on this class, not on a mail API. Swapping in a real sender
 * later touches this file only — the Dependency Inversion Principle applied where it
 * actually pays off.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter HUMAN =
            DateTimeFormatter.ofPattern("EEE dd MMM 'at' HH:mm");

    @Value("${app.timezone:Africa/Tunis}")
    private String timezone;

    /** "Your match is tomorrow at 18:00." */
    public void bookingReminder(Booking booking) {
        send(booking.getUser(), "Reminder: your booking",
                "Your court %s at %s is booked for %s."
                        .formatted(booking.getCourt().getName(),
                                   booking.getCourt().getClub().getName(),
                                   human(booking.getStartTime())));
    }

    /** "The slot you were waiting for is free — first come, first served." */
    public void waitlistSlotAvailable(WaitlistEntry entry) {
        send(entry.getUser(), "A slot just opened up",
                "The slot on %s at %s is free again. It is not reserved for you — book it quickly."
                        .formatted(entry.getCourt().getName(), human(entry.getStartTime())));
    }

    /** "Your hold expired because payment did not complete." */
    public void holdExpired(Booking booking) {
        send(booking.getUser(), "Your reservation was released",
                "The hold on %s for %s expired before payment completed."
                        .formatted(booking.getCourt().getName(), human(booking.getStartTime())));
    }

    private void send(User user, String subject, String body) {
        // One log line per notification: in production this is the audit trail that
        // answers "did we actually tell the customer?".
        log.info("NOTIFY [{}] to={} :: {}", subject, user.getEmail(), body);
    }

    private String human(OffsetDateTime instant) {
        return instant.atZoneSameInstant(ZoneId.of(timezone)).format(HUMAN);
    }
}
