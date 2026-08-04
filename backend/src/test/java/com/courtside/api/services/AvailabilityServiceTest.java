package com.courtside.api.services;

import static com.courtside.api.TestFixtures.booking;
import static com.courtside.api.TestFixtures.club;
import static com.courtside.api.TestFixtures.court;
import static com.courtside.api.TestFixtures.manager;
import static com.courtside.api.TestFixtures.member;
import static com.courtside.api.TestFixtures.openingHours;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.courtside.api.dtos.AvailabilityResponse;
import com.courtside.api.entities.Booking;
import com.courtside.api.entities.BookingStatus;
import com.courtside.api.entities.Club;
import com.courtside.api.entities.Court;
import com.courtside.api.entities.OpeningHours;
import com.courtside.api.repositories.BookingRepository;
import com.courtside.api.repositories.OpeningHoursRepository;

/**
 * Pure unit tests: no Spring context, no database. The collaborators are mocked, so
 * these run in milliseconds and fail for exactly one reason — the slot-generation
 * logic itself.
 *
 * @ExtendWith(MockitoExtension.class) creates the @Mock objects and injects them into
 * the @InjectMocks instance before every test.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AvailabilityService — slot grid generation")
class AvailabilityServiceTest {

    private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

    @Mock
    private CourtService courtService;
    @Mock
    private OpeningHoursRepository openingHoursRepository;
    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private AvailabilityService availabilityService;

    private Club club;
    private Court court;
    private LocalDate monday;

    @BeforeEach
    void setUp() {
        club = club(1L, manager(10L));
        court = court(5L, club);           // 90-minute slots, 60.00
        monday = LocalDate.of(2026, 8, 3); // a Monday -> ISO day 1

        // @Value fields are not injected by Mockito (no Spring context), so the zone
        // would be null. ReflectionTestUtils sets it the way Spring would at runtime.
        ReflectionTestUtils.setField(availabilityService, "timezone", "Africa/Tunis");
    }

    /** Helper: an instant on the test Monday, in the club's local time. */
    private OffsetDateTime at(int hour, int minute) {
        return monday.atTime(hour, minute).atZone(TUNIS).toOffsetDateTime();
    }

    private void openFrom8To22() {
        when(openingHoursRepository.findByClubIdAndDayOfWeek(club.getId(), 1))
                .thenReturn(Optional.of(openingHours(club, 1)));
    }

    private void noBookings() {
        when(bookingRepository.findOverlapping(anyLong(), any(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("generates whole slots only, from opening to closing time")
    void getForDay_whenClubOpen_generatesWholeSlotsOnly() {
        when(courtService.getById(5L)).thenReturn(court);
        openFrom8To22();
        noBookings();

        AvailabilityResponse response = availabilityService.getForDay(5L, monday);

        // 08:00 -> 22:00 is 840 minutes; 840 / 90 = 9.33 -> 9 whole slots.
        // The 10th would end at 23:30, past closing: a partial slot is never offered.
        assertThat(response.clubOpen()).isTrue();
        assertThat(response.slots()).hasSize(9);
        assertThat(response.slots().get(0).start()).isEqualTo(at(8, 0));
        assertThat(response.slots().get(0).end()).isEqualTo(at(9, 30));
        assertThat(response.slots().get(8).end()).isEqualTo(at(21, 30));
    }

    @Test
    @DisplayName("slots are contiguous: each one starts exactly where the previous ended")
    void getForDay_generatesContiguousSlots() {
        when(courtService.getById(5L)).thenReturn(court);
        openFrom8To22();
        noBookings();

        List<AvailabilityResponse.Slot> slots = availabilityService.getForDay(5L, monday).slots();

        for (int i = 1; i < slots.size(); i++) {
            assertThat(slots.get(i).start())
                    .as("slot %d must start when slot %d ends", i, i - 1)
                    .isEqualTo(slots.get(i - 1).end());
        }
    }

    @Test
    @DisplayName("every slot carries the court's current price")
    void getForDay_slotsCarryCourtPrice() {
        when(courtService.getById(5L)).thenReturn(court);
        openFrom8To22();
        noBookings();

        assertThat(availabilityService.getForDay(5L, monday).slots())
                .allSatisfy(slot -> assertThat(slot.price()).isEqualByComparingTo("60.00"));
    }

    @Test
    @DisplayName("a booked slot is marked unavailable, the others stay free")
    void getForDay_whenSlotBooked_marksOnlyThatSlotUnavailable() {
        when(courtService.getById(5L)).thenReturn(court);
        openFrom8To22();
        Booking taken = booking(1L, court, member(2L), at(9, 30), at(11, 0), BookingStatus.CONFIRMED);
        when(bookingRepository.findOverlapping(anyLong(), any(), any(), any()))
                .thenReturn(List.of(taken));

        List<AvailabilityResponse.Slot> slots = availabilityService.getForDay(5L, monday).slots();

        assertThat(slots.get(0).available()).as("08:00 untouched").isTrue();
        assertThat(slots.get(1).available()).as("09:30 is booked").isFalse();
        assertThat(slots.get(2).available()).as("11:00 untouched").isTrue();
    }

    @Test
    @DisplayName("a booking ending exactly when a slot starts does NOT block it (half-open ranges)")
    void getForDay_whenBookingTouchesSlotBoundary_slotStaysAvailable() {
        when(courtService.getById(5L)).thenReturn(court);
        openFrom8To22();
        // Ends at 09:30, which is exactly when slot #1 begins. [a,b) and [b,c) do not
        // overlap — the same rule the database applies through tstzrange.
        Booking earlier = booking(1L, court, member(2L), at(8, 0), at(9, 30), BookingStatus.CONFIRMED);
        when(bookingRepository.findOverlapping(anyLong(), any(), any(), any()))
                .thenReturn(List.of(earlier));

        List<AvailabilityResponse.Slot> slots = availabilityService.getForDay(5L, monday).slots();

        assertThat(slots.get(0).available()).as("08:00 is the booked one").isFalse();
        assertThat(slots.get(1).available()).as("09:30 only touches it, stays free").isTrue();
    }

    @Test
    @DisplayName("a partially overlapping booking blocks the slot it overlaps")
    void getForDay_whenBookingPartiallyOverlaps_marksSlotUnavailable() {
        when(courtService.getById(5L)).thenReturn(court);
        openFrom8To22();
        // 10:00-11:30 straddles the 09:30 and 11:00 slots.
        Booking straddling = booking(1L, court, member(2L), at(10, 0), at(11, 30), BookingStatus.CONFIRMED);
        when(bookingRepository.findOverlapping(anyLong(), any(), any(), any()))
                .thenReturn(List.of(straddling));

        List<AvailabilityResponse.Slot> slots = availabilityService.getForDay(5L, monday).slots();

        assertThat(slots.get(1).available()).as("09:30-11:00 overlaps").isFalse();
        assertThat(slots.get(2).available()).as("11:00-12:30 overlaps").isFalse();
        assertThat(slots.get(3).available()).as("12:30 is clear").isTrue();
    }

    @Test
    @DisplayName("a closed day returns an empty grid, not an error")
    void getForDay_whenClubClosedThatDay_returnsEmptyGrid() {
        when(courtService.getById(5L)).thenReturn(court);
        when(openingHoursRepository.findByClubIdAndDayOfWeek(club.getId(), 1))
                .thenReturn(Optional.empty());

        AvailabilityResponse response = availabilityService.getForDay(5L, monday);

        // "Closed" is a normal answer to a legitimate question — a 404 would be wrong.
        assertThat(response.clubOpen()).isFalse();
        assertThat(response.slots()).isEmpty();
    }

    @Test
    @DisplayName("an inactive court offers nothing even when the club is open")
    void getForDay_whenCourtInactive_returnsEmptyGrid() {
        court.setActive(false);
        when(courtService.getById(5L)).thenReturn(court);
        when(openingHoursRepository.findByClubIdAndDayOfWeek(club.getId(), 1))
                .thenReturn(Optional.of(openingHours(club, 1)));

        AvailabilityResponse response = availabilityService.getForDay(5L, monday);

        assertThat(response.clubOpen()).isFalse();
        assertThat(response.slots()).isEmpty();
    }

    @Test
    @DisplayName("slot count follows the court's slot length")
    void getForDay_whenSlotLengthDiffers_slotCountFollows() {
        court.setSlotMinutes(60);
        when(courtService.getById(5L)).thenReturn(court);
        when(openingHoursRepository.findByClubIdAndDayOfWeek(club.getId(), 1))
                .thenReturn(Optional.of(openingHours(club, 1, LocalTime.of(8, 0), LocalTime.of(12, 0))));
        noBookings();

        // 4 hours / 60 minutes = exactly 4 slots.
        assertThat(availabilityService.getForDay(5L, monday).slots()).hasSize(4);
    }

    @Test
    @DisplayName("the grid is asked for the right court and the right day window")
    void getForDay_queriesBookingsForTheWholeDayOnce() {
        when(courtService.getById(5L)).thenReturn(court);
        openFrom8To22();
        noBookings();

        availabilityService.getForDay(5L, monday);

        // One query for the day, not one per slot: the N+1 trap this design avoids.
        org.mockito.Mockito.verify(bookingRepository, org.mockito.Mockito.times(1))
                .findOverlapping(org.mockito.ArgumentMatchers.eq(5L),
                        org.mockito.ArgumentMatchers.eq(at(8, 0)),
                        org.mockito.ArgumentMatchers.eq(at(22, 0)),
                        any());
    }
}
