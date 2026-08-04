package com.courtside.api.services;

import static com.courtside.api.TestFixtures.booking;
import static com.courtside.api.TestFixtures.club;
import static com.courtside.api.TestFixtures.court;
import static com.courtside.api.TestFixtures.manager;
import static com.courtside.api.TestFixtures.member;
import static com.courtside.api.TestFixtures.openingHours;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import com.courtside.api.dtos.BookingRequest;
import com.courtside.api.entities.Booking;
import com.courtside.api.entities.BookingStatus;
import com.courtside.api.entities.Club;
import com.courtside.api.entities.Court;
import com.courtside.api.entities.User;
import com.courtside.api.exceptions.BusinessRuleException;
import com.courtside.api.exceptions.SlotUnavailableException;
import com.courtside.api.repositories.BookingRepository;
import com.courtside.api.repositories.OpeningHoursRepository;

/**
 * The booking rules, tested without a database.
 *
 * Every rejection here is a 422 in production; every one of them exists because
 * without it a user could corrupt the schedule (book the past, book 5 minutes to
 * squat a slot, book while the club is closed, or drift off the published grid).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService — business rules")
class BookingServiceRulesTest {

    private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

    @Mock private BookingRepository bookingRepository;
    @Mock private OpeningHoursRepository openingHoursRepository;
    @Mock private CourtService courtService;
    @Mock private CacheManager cacheManager;
    @Mock private AvailabilityEventPublisher eventPublisher;
    @Mock private NotificationService notificationService;
    @Mock private WaitlistService waitlistService;

    @InjectMocks
    private BookingService bookingService;

    private Club club;
    private Court court;
    private User alice;
    private LocalDate tomorrow;

    @BeforeEach
    void setUp() {
        club = club(1L, manager(10L));
        court = court(5L, club);
        alice = member(2L);
        tomorrow = LocalDate.now(TUNIS).plusDays(1);

        ReflectionTestUtils.setField(bookingService, "timezone", "Africa/Tunis");
        ReflectionTestUtils.setField(bookingService, "cancelHoursBefore", 2L);
        ReflectionTestUtils.setField(bookingService, "holdMinutes", 10L);

        // lenient: several tests reject the request before ever reaching this stub,
        // and strict stubbing would then fail them for an "unnecessary" stub.
        lenient().when(courtService.getByIdWithClub(5L)).thenReturn(court);
    }

    private OffsetDateTime at(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(TUNIS).toOffsetDateTime();
    }

    private BookingRequest request(OffsetDateTime start, OffsetDateTime end) {
        return new BookingRequest(5L, start, end);
    }

    private void openAllDay() {
        when(openingHoursRepository.findByClubIdAndDayOfWeek(anyLong(), any()))
                .thenReturn(Optional.of(openingHours(club, tomorrow.getDayOfWeek().getValue())));
    }

    // ---------------- happy path ----------------

    @Test
    @DisplayName("the price comes from the court, never from the client")
    void create_alwaysPricesFromTheCourt() {
        openAllDay();
        when(bookingRepository.findOverlapping(anyLong(), any(), any(), any())).thenReturn(List.of());
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        bookingService.create(request(at(tomorrow, 8, 0), at(tomorrow, 9, 30)), alice);

        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        org.mockito.Mockito.verify(bookingRepository).saveAndFlush(saved.capture());

        // The request DTO has no price field at all — this asserts the design holds.
        assertThat(saved.getValue().getPrice()).isEqualByComparingTo(new BigDecimal("60.00"));
        assertThat(saved.getValue().getUser()).isSameAs(alice);
        assertThat(saved.getValue().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    // ---------------- rejections ----------------

    @Test
    @DisplayName("a slot in the past is refused")
    void create_whenStartInThePast_throws() {
        LocalDate yesterday = LocalDate.now(TUNIS).minusDays(1);

        assertThatThrownBy(() -> bookingService.create(
                request(at(yesterday, 8, 0), at(yesterday, 9, 30)), alice))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("past");
    }

    @Test
    @DisplayName("end before start is refused")
    void create_whenEndBeforeStart_throws() {
        assertThatThrownBy(() -> bookingService.create(
                request(at(tomorrow, 10, 0), at(tomorrow, 9, 0)), alice))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("a duration other than one slot is refused")
    void create_whenDurationIsNotOneSlot_throws() {
        // 60 minutes on a 90-minute court: allowing it would let a user block a slot
        // with a shorter booking and fragment the published grid.
        assertThatThrownBy(() -> bookingService.create(
                request(at(tomorrow, 8, 0), at(tomorrow, 9, 0)), alice))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("90");
    }

    @Test
    @DisplayName("an inactive court cannot be booked")
    void create_whenCourtInactive_throws() {
        court.setActive(false);

        assertThatThrownBy(() -> bookingService.create(
                request(at(tomorrow, 8, 0), at(tomorrow, 9, 30)), alice))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not bookable");
    }

    @Test
    @DisplayName("booking on a day the club is closed is refused")
    void create_whenClubClosedThatDay_throws() {
        when(openingHoursRepository.findByClubIdAndDayOfWeek(anyLong(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.create(
                request(at(tomorrow, 8, 0), at(tomorrow, 9, 30)), alice))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("closed");
    }

    @Test
    @DisplayName("a slot outside opening hours is refused")
    void create_whenOutsideOpeningHours_throws() {
        openAllDay(); // 08:00–22:00

        assertThatThrownBy(() -> bookingService.create(
                request(at(tomorrow, 5, 0), at(tomorrow, 6, 30)), alice))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("opening hours");
    }

    @Test
    @DisplayName("a start that is not on the slot grid is refused")
    void create_whenMisalignedWithGrid_throws() {
        openAllDay(); // opens 08:00, 90-minute slots -> 08:00, 09:30, 11:00...

        assertThatThrownBy(() -> bookingService.create(
                request(at(tomorrow, 8, 45), at(tomorrow, 10, 15)), alice))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("align");
    }

    @Test
    @DisplayName("an already-booked slot is refused before touching the database")
    void create_whenSlotAlreadyBooked_throwsSlotUnavailable() {
        openAllDay();
        Booking existing = booking(9L, court, member(3L),
                at(tomorrow, 8, 0), at(tomorrow, 9, 30), BookingStatus.CONFIRMED);
        when(bookingRepository.findOverlapping(anyLong(), any(), any(), any()))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> bookingService.create(
                request(at(tomorrow, 8, 0), at(tomorrow, 9, 30)), alice))
                .isInstanceOf(SlotUnavailableException.class);

        // The friendly pre-check must short-circuit: no INSERT is attempted.
        org.mockito.Mockito.verify(bookingRepository, org.mockito.Mockito.never())
                .saveAndFlush(any());
    }

    // ---------------- holds ----------------

    @Test
    @DisplayName("a hold is created with an expiry deadline")
    void createHold_setsHoldStatusAndDeadline() {
        openAllDay();
        when(bookingRepository.findOverlapping(anyLong(), any(), any(), any())).thenReturn(List.of());
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Booking hold = bookingService.createHold(
                request(at(tomorrow, 8, 0), at(tomorrow, 9, 30)), alice);

        assertThat(hold.getStatus()).isEqualTo(BookingStatus.HOLD);
        assertThat(hold.getHoldExpiresAt())
                .isNotNull()
                .isAfter(OffsetDateTime.now())
                .isBefore(OffsetDateTime.now().plusMinutes(11));
    }
}
