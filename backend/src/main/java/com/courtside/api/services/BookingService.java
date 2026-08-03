package com.courtside.api.services;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.courtside.api.dtos.BookingRequest;
import com.courtside.api.entities.Booking;
import com.courtside.api.entities.BookingStatus;
import com.courtside.api.entities.Court;
import com.courtside.api.entities.OpeningHours;
import com.courtside.api.entities.Role;
import com.courtside.api.entities.User;
import com.courtside.api.exceptions.BusinessRuleException;
import com.courtside.api.exceptions.NotFoundException;
import com.courtside.api.exceptions.SlotUnavailableException;
import com.courtside.api.repositories.BookingRepository;
import com.courtside.api.repositories.OpeningHoursRepository;

@Service
public class BookingService {

    private static final List<BookingStatus> ACTIVE =
            List.of(BookingStatus.HOLD, BookingStatus.CONFIRMED);

    private final BookingRepository bookingRepository;
    private final OpeningHoursRepository openingHoursRepository;
    private final CourtService courtService;
    private final CacheManager cacheManager;

    @Value("${app.timezone:Africa/Tunis}")
    private String timezone;

    /** Free cancellation window, in hours before the start. */
    @Value("${app.booking.cancel-hours-before:2}")
    private long cancelHoursBefore;

    public BookingService(BookingRepository bookingRepository,
                          OpeningHoursRepository openingHoursRepository,
                          CourtService courtService,
                          CacheManager cacheManager) {
        this.bookingRepository = bookingRepository;
        this.openingHoursRepository = openingHoursRepository;
        this.courtService = courtService;
        this.cacheManager = cacheManager;
    }

    /**
     * A booking just changed one day of one court: drop that cache entry so the next
     * reader recomputes the grid.
     *
     * Done by hand rather than with @CacheEvict because the cache key is
     * "courtId:localDate" and the local date only exists after converting the stored
     * instant into the club's zone — a SpEL expression cannot do that readably.
     *
     * Evicting one precise key (not the whole cache) keeps every other court's grid warm.
     */
    private void evictAvailability(Court court, OffsetDateTime start) {
        Cache cache = cacheManager.getCache("availability");
        if (cache != null) {
            LocalDate localDate = start.atZoneSameInstant(ZoneId.of(timezone)).toLocalDate();
            cache.evict(court.getId() + ":" + localDate);
        }
    }

    /**
     * Creates a booking — the operation this whole project is built around.
     *
     * Defence in depth against double booking:
     *  1. Java validates the request and checks availability -> friendly errors.
     *  2. The DATABASE has the final word through the `no_overlapping_bookings`
     *     exclusion constraint. Two requests can both pass step 1 concurrently
     *     (neither has committed yet, so neither sees the other), but only one
     *     transaction can commit. The loser lands in the catch below.
     *
     * Never remove step 2 because step 1 exists: application checks cannot be atomic
     * across transactions. And never remove step 1 because step 2 exists: users
     * deserve a readable message before they hit the wall.
     */
    @Transactional
    public Booking create(BookingRequest request, User currentUser) {
        // WithClub: the response DTO reads court.getClub().getName() after this
        // transaction commits, so the club must already be loaded (no lazy proxy).
        Court court = courtService.getByIdWithClub(request.courtId());

        validateTiming(court, request.start(), request.end());

        // Friendly pre-check. Purely advisory — see the comment above.
        List<Booking> clashes = bookingRepository.findOverlapping(
                court.getId(), request.start(), request.end(), ACTIVE);
        if (!clashes.isEmpty()) {
            throw new SlotUnavailableException("This slot is already booked");
        }

        Booking booking = new Booking();
        booking.setCourt(court);
        booking.setUser(currentUser);
        booking.setStartTime(request.start());
        booking.setEndTime(request.end());
        booking.setStatus(BookingStatus.CONFIRMED);
        // Price computed server-side from the court, never taken from the request.
        booking.setPrice(court.getPricePerSlot());

        try {
            // saveAndFlush, not save: force the INSERT to hit the database NOW so the
            // constraint fires inside this try block. With a plain save() the flush
            // would happen at commit — outside our reach — and the user would get a
            // raw 500 instead of a clean 409.
            Booking saved = bookingRepository.saveAndFlush(booking);
            evictAvailability(court, saved.getStartTime());
            return saved;
        } catch (DataIntegrityViolationException e) {
            // The exclusion constraint rejected us: someone else committed the same
            // slot microseconds earlier. This is the race we cannot prevent, only lose
            // gracefully.
            throw new SlotUnavailableException("This slot was just taken by someone else");
        }
    }

    @Transactional(readOnly = true)
    public Page<Booking> listMine(User user, boolean upcomingOnly, Pageable pageable) {
        return bookingRepository.findMine(user.getId(), upcomingOnly, OffsetDateTime.now(), pageable);
    }

    @Transactional(readOnly = true)
    public Booking getById(Long id, User currentUser) {
        Booking booking = bookingRepository.findByIdDetailed(id)
                .orElseThrow(() -> new NotFoundException("Booking", id));
        requireCanSee(booking, currentUser);
        return booking;
    }

    /**
     * Cancels a booking. The row is kept with status CANCELLED — the exclusion
     * constraint ignores cancelled rows, so the slot becomes bookable again while
     * the history survives (who booked what, and when they cancelled).
     */
    @Transactional
    public Booking cancel(Long id, User currentUser) {
        Booking booking = bookingRepository.findByIdDetailed(id)
                .orElseThrow(() -> new NotFoundException("Booking", id));
        requireCanSee(booking, currentUser);

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BusinessRuleException("This booking is already cancelled");
        }

        // Staff may cancel at any time (a club has to handle real-world incidents);
        // members are held to the cancellation window.
        boolean isStaff = currentUser.getRole() != Role.MEMBER;
        OffsetDateTime deadline = booking.getStartTime().minusHours(cancelHoursBefore);

        if (!isStaff && OffsetDateTime.now().isAfter(deadline)) {
            throw new BusinessRuleException(
                    "Cancellation is only possible until " + cancelHoursBefore + "h before the start");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        // The slot is free again — the cached grid must not keep showing it as taken.
        evictAvailability(booking.getCourt(), booking.getStartTime());
        return booking;
    }

    // ------------------------------------------------------------------
    // Business rules
    // ------------------------------------------------------------------

    private void validateTiming(Court court, OffsetDateTime start, OffsetDateTime end) {
        if (!start.isBefore(end)) {
            throw new BusinessRuleException("The start must be before the end");
        }
        if (start.isBefore(OffsetDateTime.now())) {
            throw new BusinessRuleException("You cannot book a slot in the past");
        }
        if (!court.isActive()) {
            throw new BusinessRuleException("This court is not bookable");
        }

        // The requested duration must be exactly one slot: allowing arbitrary ranges
        // would let a user book 08:00-08:05 and block the whole 08:00 slot.
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes != court.getSlotMinutes()) {
            throw new BusinessRuleException(
                    "A booking must last exactly " + court.getSlotMinutes() + " minutes");
        }

        ZoneId zone = ZoneId.of(timezone);
        var localStart = start.atZoneSameInstant(zone);
        var localEnd = end.atZoneSameInstant(zone);

        OpeningHours hours = openingHoursRepository
                .findByClubIdAndDayOfWeek(court.getClub().getId(), localStart.getDayOfWeek().getValue())
                .orElseThrow(() -> new BusinessRuleException("The club is closed on that day"));

        OffsetDateTime open = LocalDateTime.of(localStart.toLocalDate(), hours.getOpens())
                .atZone(zone).toOffsetDateTime();
        OffsetDateTime close = LocalDateTime.of(localStart.toLocalDate(), hours.getCloses())
                .atZone(zone).toOffsetDateTime();

        if (start.isBefore(open) || end.isAfter(close)) {
            throw new BusinessRuleException(
                    "The slot must be within opening hours (" + hours.getOpens() + " - " + hours.getCloses() + ")");
        }

        // Slots must sit on the grid the availability endpoint publishes, otherwise a
        // 08:45 booking would fragment the day and make the grid lie.
        long offsetMinutes = Duration.between(open, start).toMinutes();
        if (offsetMinutes % court.getSlotMinutes() != 0) {
            throw new BusinessRuleException("The slot must align with the court's schedule");
        }
        // localEnd is unused beyond validation above; kept for readability of intent.
        if (localEnd.toLocalDate().isAfter(localStart.toLocalDate().plusDays(1))) {
            throw new BusinessRuleException("A booking cannot span several days");
        }
    }

    /** Owner, club staff and admins may see or cancel a booking; nobody else. */
    private void requireCanSee(Booking booking, User currentUser) {
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        boolean isOwner = booking.getUser().getId().equals(currentUser.getId());
        var manager = booking.getCourt().getClub().getManager();
        boolean isClubManager = manager != null && manager.getId().equals(currentUser.getId());

        if (!isOwner && !isClubManager) {
            throw new AccessDeniedException("This booking is not yours");
        }
    }
}
