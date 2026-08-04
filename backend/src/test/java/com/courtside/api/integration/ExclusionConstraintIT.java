package com.courtside.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.courtside.api.entities.Booking;
import com.courtside.api.entities.BookingStatus;
import com.courtside.api.entities.Club;
import com.courtside.api.entities.Court;
import com.courtside.api.entities.OpeningHours;
import com.courtside.api.entities.Role;
import com.courtside.api.entities.Sport;
import com.courtside.api.entities.User;
import com.courtside.api.repositories.BookingRepository;
import com.courtside.api.repositories.ClubRepository;
import com.courtside.api.repositories.CourtRepository;
import com.courtside.api.repositories.OpeningHoursRepository;
import com.courtside.api.repositories.UserRepository;

/**
 * Proves the invariant the whole product rests on, against a real PostgreSQL:
 * two active bookings can never overlap on the same court.
 *
 * These assertions are impossible to make in a unit test — the rule does not live in
 * Java at all, it lives in the schema.
 */
@DisplayName("PostgreSQL exclusion constraint — no overlapping bookings")
class ExclusionConstraintIT extends AbstractIntegrationTest {

    private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");

    @Autowired private BookingRepository bookingRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OpeningHoursRepository openingHoursRepository;
    @Autowired private JdbcTemplate jdbc;

    private Court court;
    private User user;

    @BeforeEach
    void setUp() {
        // Order matters: bookings reference courts and users.
        jdbc.update("DELETE FROM bookings");
        jdbc.update("DELETE FROM waitlist_entries");
        jdbc.update("DELETE FROM opening_hours");
        jdbc.update("DELETE FROM courts");
        jdbc.update("DELETE FROM clubs");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM users");

        user = new User();
        user.setEmail("it-user@test.tn");
        user.setFullName("IT User");
        user.setPasswordHash("$2a$10$notarealhash");
        user.setRole(Role.MEMBER);
        user = userRepository.save(user);

        Club club = new Club();
        club.setName("IT Club");
        club.setCity("Tunis");
        club.setAddress("Test street");
        club.setManager(null);
        club = clubRepository.save(club);

        OpeningHours hours = new OpeningHours();
        hours.setClub(club);
        hours.setDayOfWeek(1);
        hours.setOpens(LocalTime.of(8, 0));
        hours.setCloses(LocalTime.of(22, 0));
        openingHoursRepository.save(hours);

        Court c = new Court();
        c.setClub(club);
        c.setName("IT Court");
        c.setSport(Sport.PADEL);
        c.setSlotMinutes(90);
        c.setPricePerSlot(new BigDecimal("60.00"));
        c.setActive(true);
        court = courtRepository.save(c);
    }

    private OffsetDateTime at(int hour, int minute) {
        return OffsetDateTime.now(TUNIS).plusDays(7)
                .withHour(hour).withMinute(minute).withSecond(0).withNano(0);
    }

    private Booking newBooking(OffsetDateTime start, OffsetDateTime end, BookingStatus status) {
        Booking b = new Booking();
        b.setCourt(court);
        b.setUser(user);
        b.setStartTime(start);
        b.setEndTime(end);
        b.setStatus(status);
        b.setPrice(new BigDecimal("60.00"));
        return b;
    }

    @Test
    @DisplayName("the database refuses a second booking overlapping the first")
    void insert_whenRangesOverlap_isRejectedByTheDatabase() {
        bookingRepository.saveAndFlush(newBooking(at(10, 0), at(11, 30), BookingStatus.CONFIRMED));

        // 10:45–12:15 overlaps 10:00–11:30. No Java code is involved in this refusal.
        assertThatThrownBy(() -> bookingRepository.saveAndFlush(
                newBooking(at(10, 45), at(12, 15), BookingStatus.CONFIRMED)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("back-to-back bookings are allowed (ranges are half-open)")
    void insert_whenRangesOnlyTouch_isAllowed() {
        bookingRepository.saveAndFlush(newBooking(at(10, 0), at(11, 30), BookingStatus.CONFIRMED));

        // Starts exactly when the previous ends: [a,b) and [b,c) do not overlap.
        // If this ever failed, half the published grid would become unbookable.
        assertThatCode(() -> bookingRepository.saveAndFlush(
                newBooking(at(11, 30), at(13, 0), BookingStatus.CONFIRMED)))
                .doesNotThrowAnyException();

        assertThat(bookingRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("a HOLD blocks the slot exactly like a confirmed booking")
    void insert_whenSlotIsHeld_isRejected() {
        bookingRepository.saveAndFlush(newBooking(at(10, 0), at(11, 30), BookingStatus.HOLD));

        // The constraint's WHERE clause covers HOLD too — which is what makes the
        // payment flow safe: the slot is really reserved while paying.
        assertThatThrownBy(() -> bookingRepository.saveAndFlush(
                newBooking(at(10, 0), at(11, 30), BookingStatus.CONFIRMED)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a CANCELLED booking may overlap — history is kept, the slot is reusable")
    void insert_whenPreviousIsCancelled_isAllowed() {
        Booking cancelled = bookingRepository.saveAndFlush(
                newBooking(at(10, 0), at(11, 30), BookingStatus.CANCELLED));

        assertThatCode(() -> bookingRepository.saveAndFlush(
                newBooking(at(10, 0), at(11, 30), BookingStatus.CONFIRMED)))
                .doesNotThrowAnyException();

        // Two rows for the same slot: one dead, one live. This is the partial WHERE
        // clause earning its keep — a plain UNIQUE constraint could not do this.
        assertThat(bookingRepository.count()).isEqualTo(2);
        assertThat(bookingRepository.findById(cancelled.getId()))
                .get()
                .extracting(Booking::getStatus)
                .isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("different courts at the same time never collide")
    void insert_whenDifferentCourt_isAllowed() {
        bookingRepository.saveAndFlush(newBooking(at(10, 0), at(11, 30), BookingStatus.CONFIRMED));

        Court second = new Court();
        second.setClub(court.getClub());
        second.setName("IT Court 2");
        second.setSport(Sport.PADEL);
        second.setSlotMinutes(90);
        second.setPricePerSlot(new BigDecimal("50.00"));
        second.setActive(true);
        second = courtRepository.save(second);

        Booking other = newBooking(at(10, 0), at(11, 30), BookingStatus.CONFIRMED);
        other.setCourt(second);

        // The constraint is scoped by court_id — a club can run every court at once.
        final Court finalSecond = second;
        assertThatCode(() -> bookingRepository.saveAndFlush(other)).doesNotThrowAnyException();
        assertThat(finalSecond.getId()).isNotEqualTo(court.getId());
    }

    @Test
    @DisplayName("both Flyway migrations are applied")
    void migrations_areApplied() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class);

        // Proves V1 and V2 run cleanly on an empty database — a broken migration
        // fails here instead of in production.
        assertThat(versions).containsExactly("1", "2");
    }
}
