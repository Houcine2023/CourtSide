package com.courtside.api.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.courtside.api.dtos.AvailabilityResponse;
import com.courtside.api.entities.Booking;
import com.courtside.api.entities.BookingStatus;
import com.courtside.api.entities.Court;
import com.courtside.api.entities.OpeningHours;
import com.courtside.api.repositories.BookingRepository;
import com.courtside.api.repositories.OpeningHoursRepository;

/**
 * Turns "opening hours + slot length - existing bookings" into a grid of slots.
 *
 * This service is pure computation over data it is given: no HTTP, no security. That
 * makes it the easiest class in the project to unit-test later — feed it a court, a
 * date and a list of bookings, assert the grid.
 */
@Service
public class AvailabilityService {

    /** Statuses that occupy a slot. Mirrors the WHERE clause of the DB exclusion constraint. */
    private static final List<BookingStatus> ACTIVE =
            List.of(BookingStatus.HOLD, BookingStatus.CONFIRMED);

    private final CourtService courtService;
    private final OpeningHoursRepository openingHoursRepository;
    private final BookingRepository bookingRepository;

    /** Clubs publish local opening times; we need a zone to turn them into instants. */
    @Value("${app.timezone:Africa/Tunis}")
    private String timezone;

    public AvailabilityService(CourtService courtService,
                               OpeningHoursRepository openingHoursRepository,
                               BookingRepository bookingRepository) {
        this.courtService = courtService;
        this.openingHoursRepository = openingHoursRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse getForDay(Long courtId, LocalDate date) {
        Court court = courtService.getById(courtId);
        ZoneId zone = ZoneId.of(timezone);

        // ISO weekday: Monday = 1 ... Sunday = 7, exactly how we store day_of_week.
        int dayOfWeek = date.getDayOfWeek().getValue();

        OpeningHours hours = openingHoursRepository
                .findByClubIdAndDayOfWeek(court.getClub().getId(), dayOfWeek)
                .orElse(null);

        // Closed that day (or the club never configured it): an empty grid, not an error.
        if (hours == null || !court.isActive()) {
            return new AvailabilityResponse(court.getId(), court.getName(), date, false, List.of());
        }

        OffsetDateTime dayStart = LocalDateTime.of(date, hours.getOpens()).atZone(zone).toOffsetDateTime();
        OffsetDateTime dayEnd = LocalDateTime.of(date, hours.getCloses()).atZone(zone).toOffsetDateTime();

        // ONE query for the whole day, then match slots in memory. The alternative
        // (a query per slot) is a textbook N+1: 20 slots = 20 round trips.
        List<Booking> taken = bookingRepository.findOverlapping(courtId, dayStart, dayEnd, ACTIVE);

        List<AvailabilityResponse.Slot> slots = new ArrayList<>();
        OffsetDateTime cursor = dayStart;

        while (!cursor.plusMinutes(court.getSlotMinutes()).isAfter(dayEnd)) {
            OffsetDateTime slotEnd = cursor.plusMinutes(court.getSlotMinutes());
            final OffsetDateTime slotStart = cursor;

            // Same half-open overlap test as the DB constraint: [a1,a2) vs [b1,b2)
            // overlap when a1 < b2 && a2 > b1. Back-to-back slots never collide.
            boolean occupied = taken.stream().anyMatch(b ->
                    b.getStartTime().isBefore(slotEnd) && b.getEndTime().isAfter(slotStart));

            slots.add(new AvailabilityResponse.Slot(
                    slotStart, slotEnd, !occupied, court.getPricePerSlot()));

            cursor = slotEnd;
        }

        return new AvailabilityResponse(court.getId(), court.getName(), date, true, slots);
    }
}
