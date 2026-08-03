package com.courtside.api.services;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.courtside.api.dtos.WaitlistRequest;
import com.courtside.api.entities.Booking;
import com.courtside.api.entities.Court;
import com.courtside.api.entities.Role;
import com.courtside.api.entities.User;
import com.courtside.api.entities.WaitlistEntry;
import com.courtside.api.exceptions.BusinessRuleException;
import com.courtside.api.exceptions.NotFoundException;
import com.courtside.api.repositories.WaitlistRepository;

/**
 * "Tell me if this slot frees up."
 *
 * Design decision worth defending: being notified does NOT reserve the slot. We tell
 * everyone waiting, first-come-first-served on the booking that follows. Holding the
 * slot for the first person would be friendlier but needs an expiry mechanism per
 * waitlist entry, and punishes everyone else when they do not respond. Announcing to
 * all is simple, fair, and never leaves a slot locked by someone who walked away.
 */
@Service
public class WaitlistService {

    private static final Logger log = LoggerFactory.getLogger(WaitlistService.class);

    private final WaitlistRepository waitlistRepository;
    private final CourtService courtService;
    private final NotificationService notificationService;

    public WaitlistService(WaitlistRepository waitlistRepository,
                           CourtService courtService,
                           NotificationService notificationService) {
        this.waitlistRepository = waitlistRepository;
        this.courtService = courtService;
        this.notificationService = notificationService;
    }

    @Transactional
    public WaitlistEntry join(WaitlistRequest request, User currentUser) {
        Court court = courtService.getByIdWithClub(request.courtId());

        if (request.start().isBefore(OffsetDateTime.now())) {
            throw new BusinessRuleException("You cannot wait for a slot in the past");
        }
        long minutes = Duration.between(request.start(), request.end()).toMinutes();
        if (minutes != court.getSlotMinutes()) {
            throw new BusinessRuleException(
                    "A slot lasts exactly " + court.getSlotMinutes() + " minutes");
        }

        // Explicit check for a friendly 422 instead of letting the unique index throw
        // an opaque constraint violation. The index still guards against the race.
        waitlistRepository
                .findByCourtIdAndUserIdAndStartTimeAndEndTimeAndActiveTrue(
                        court.getId(), currentUser.getId(), request.start(), request.end())
                .ifPresent(existing -> {
                    throw new BusinessRuleException("You are already on the waitlist for this slot");
                });

        WaitlistEntry entry = new WaitlistEntry();
        entry.setCourt(court);
        entry.setUser(currentUser);
        entry.setStartTime(request.start());
        entry.setEndTime(request.end());
        entry.setActive(true);
        return waitlistRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<WaitlistEntry> listMine(User currentUser) {
        return waitlistRepository.findMine(currentUser.getId());
    }

    @Transactional
    public void leave(Long id, User currentUser) {
        WaitlistEntry entry = waitlistRepository.findByIdWithUser(id)
                .orElseThrow(() -> new NotFoundException("Waitlist entry", id));

        if (currentUser.getRole() != Role.ADMIN
                && !entry.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("This waitlist entry is not yours");
        }
        // Deactivate rather than delete: the unique index only guards active rows, so
        // the user can re-join later, and we keep the record of who waited for what.
        entry.setActive(false);
    }

    /**
     * A booking disappeared (cancelled, or a hold expired) — tell everyone queued for
     * that exact slot, oldest first.
     *
     * Called from BookingService inside the same transaction, so if the cancellation
     * rolls back nobody is told about a slot that is still taken.
     */
    @Transactional
    public int notifySlotFreed(Booking booking) {
        List<WaitlistEntry> waiting = waitlistRepository.findWaiting(
                booking.getCourt().getId(), booking.getStartTime(), booking.getEndTime());

        for (WaitlistEntry entry : waiting) {
            notificationService.waitlistSlotAvailable(entry);
            entry.setNotifiedAt(OffsetDateTime.now());
            // Consumed: the promise was "tell me once it frees", and it has been kept.
            entry.setActive(false);
        }

        if (!waiting.isEmpty()) {
            log.info("Waitlist: notified {} user(s) that court {} is free at {}",
                    waiting.size(), booking.getCourt().getId(), booking.getStartTime());
        }
        return waiting.size();
    }

    /** Housekeeping: entries for slots that have already started can never fire. */
    @Transactional
    public int deactivateStale() {
        List<WaitlistEntry> stale = waitlistRepository.findStale(OffsetDateTime.now());
        stale.forEach(e -> e.setActive(false));
        return stale.size();
    }
}
