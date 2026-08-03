package com.courtside.api.services;

import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.courtside.api.entities.Booking;
import com.courtside.api.repositories.BookingRepository;

/**
 * Scheduled background work around bookings.
 *
 * Kept in one class so every recurring job is visible in one place — scattering
 * @Scheduled methods across services makes it impossible to answer "what runs at
 * 3am?". Remember these do nothing without @EnableScheduling on the application class.
 */
@Component
public class BookingJobs {

    private static final Logger log = LoggerFactory.getLogger(BookingJobs.class);

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final WaitlistService waitlistService;
    private final NotificationService notificationService;

    /** How far ahead a reminder is sent. */
    @Value("${app.booking.reminder-hours-before:24}")
    private long reminderHoursBefore;

    public BookingJobs(BookingRepository bookingRepository,
                       BookingService bookingService,
                       WaitlistService waitlistService,
                       NotificationService notificationService) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.waitlistService = waitlistService;
        this.notificationService = notificationService;
    }

    /**
     * Frees slots whose payment window closed.
     *
     * fixedDelay, not fixedRate: fixedRate fires every N ms regardless of how long the
     * previous run took, so a slow run can overlap itself. fixedDelay waits N ms AFTER
     * the previous run finished — the safe default for jobs touching the database.
     *
     * Every minute: a 10-minute hold is never held more than ~11 minutes, which is
     * accurate enough while keeping the query rate trivial.
     */
    @Scheduled(fixedDelayString = "${app.jobs.hold-release-ms:60000}")
    public void releaseExpiredHolds() {
        try {
            int released = bookingService.releaseExpiredHolds();
            if (released > 0) {
                log.info("Released {} expired hold(s)", released);
            }
        } catch (Exception e) {
            // A scheduled method that throws is simply not rescheduled by some
            // executors, and silently dies. Always contain your own failures.
            log.error("Hold-release job failed", e);
        }
    }

    /**
     * Reminds users about bookings starting in ~24h.
     *
     * The window is [target, target + 1h) and the job runs hourly, so each booking
     * falls in exactly one window: no duplicate reminders, none skipped. Deriving
     * idempotency from the WINDOW rather than from a "reminded" flag keeps the schema
     * simpler — the trade-off is that a missed run means a missed reminder, which for
     * a courtesy message is acceptable.
     */
    @Scheduled(cron = "${app.jobs.reminder-cron:0 0 * * * *}")
    @Transactional(readOnly = true)
    public void sendBookingReminders() {
        try {
            OffsetDateTime windowStart = OffsetDateTime.now().plusHours(reminderHoursBefore);
            OffsetDateTime windowEnd = windowStart.plusHours(1);

            List<Booking> upcoming = bookingRepository.findStartingBetween(windowStart, windowEnd);
            upcoming.forEach(notificationService::bookingReminder);

            if (!upcoming.isEmpty()) {
                log.info("Sent {} booking reminder(s) for slots starting around {}",
                        upcoming.size(), windowStart);
            }
        } catch (Exception e) {
            log.error("Reminder job failed", e);
        }
    }

    /** Nightly: waitlist entries whose slot has already started can never fire. */
    @Scheduled(cron = "${app.jobs.waitlist-cleanup-cron:0 30 3 * * *}")
    public void cleanupStaleWaitlist() {
        try {
            int cleaned = waitlistService.deactivateStale();
            if (cleaned > 0) {
                log.info("Deactivated {} stale waitlist entr(ies)", cleaned);
            }
        } catch (Exception e) {
            log.error("Waitlist cleanup job failed", e);
        }
    }
}
