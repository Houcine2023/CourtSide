package com.courtside.api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.courtside.api.dtos.BookingRequest;
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
import com.courtside.api.services.BookingService;

/**
 * THE test this project was designed around.
 *
 * Ten users click "book" on the same slot in the same instant. The Java pre-check
 * cannot save us — all ten transactions run concurrently and none of them can see the
 * others' uncommitted rows. Only the database can arbitrate, and it must let exactly
 * one through.
 *
 * A test like this is why the manual "run the script and count" ritual had to become
 * code: it is the one guarantee that can silently break during a refactor, and the one
 * whose failure would be visible to customers as a double-booked court.
 */
@DisplayName("Concurrency — ten simultaneous bookings for one slot")
class ConcurrentBookingIT extends AbstractIntegrationTest {

    private static final ZoneId TUNIS = ZoneId.of("Africa/Tunis");
    private static final int THREADS = 10;

    @Autowired private BookingService bookingService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OpeningHoursRepository openingHoursRepository;
    @Autowired private JdbcTemplate jdbc;

    private Court court;
    private List<User> contenders;
    private OffsetDateTime slotStart;
    private OffsetDateTime slotEnd;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM bookings");
        jdbc.update("DELETE FROM waitlist_entries");
        jdbc.update("DELETE FROM opening_hours");
        jdbc.update("DELETE FROM courts");
        jdbc.update("DELETE FROM clubs");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM users");

        Club club = new Club();
        club.setName("Race Club");
        club.setCity("Tunis");
        club.setAddress("Race street");
        club = clubRepository.save(club);

        // Open every day, so the test never fails because "tomorrow" is a Sunday.
        for (int day = 1; day <= 7; day++) {
            OpeningHours oh = new OpeningHours();
            oh.setClub(club);
            oh.setDayOfWeek(day);
            oh.setOpens(LocalTime.of(8, 0));
            oh.setCloses(LocalTime.of(22, 0));
            openingHoursRepository.save(oh);
        }

        Court c = new Court();
        c.setClub(club);
        c.setName("Race Court");
        c.setSport(Sport.PADEL);
        c.setSlotMinutes(90);
        c.setPricePerSlot(new BigDecimal("60.00"));
        c.setActive(true);
        court = courtRepository.save(c);

        contenders = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            User u = new User();
            u.setEmail("racer" + i + "@test.tn");
            u.setFullName("Racer " + i);
            u.setPasswordHash("$2a$10$notarealhash");
            u.setRole(Role.MEMBER);
            contenders.add(userRepository.save(u));
        }

        // Tomorrow at 08:00 — the first slot on the grid, so it is aligned by construction.
        slotStart = OffsetDateTime.now(TUNIS).plusDays(1)
                .withHour(8).withMinute(0).withSecond(0).withNano(0);
        slotEnd = slotStart.plusMinutes(90);
    }

    @Test
    @DisplayName("exactly one booking survives; the other nine are refused")
    void create_whenTenUsersBookTheSameSlotSimultaneously_onlyOneSucceeds() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        // A start gate: every thread blocks here, so they are released together instead
        // of trickling in as they are scheduled. Without it the first would commit long
        // before the last even started, and the race would never actually happen.
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        List<Callable<Void>> tasks = new ArrayList<>();
        for (User contender : contenders) {
            tasks.add(() -> {
                startGate.await();
                try {
                    bookingService.create(new BookingRequest(court.getId(), slotStart, slotEnd), contender);
                    succeeded.incrementAndGet();
                } catch (com.courtside.api.exceptions.SlotUnavailableException expected) {
                    // Either the pre-check saw a committed row, or the database
                    // constraint rejected the INSERT. Both are correct outcomes.
                    refused.incrementAndGet();
                } catch (Exception other) {
                    // Anything else means a leak: a raw 500 would reach the user.
                    unexpected.incrementAndGet();
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(pool.submit(task));
        }
        startGate.countDown();               // release all ten at once
        for (Future<Void> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(succeeded.get()).as("exactly one booking must be created").isEqualTo(1);
        assertThat(refused.get()).as("the other nine get a clean 409").isEqualTo(THREADS - 1);
        assertThat(unexpected.get()).as("no unexpected exception may escape").isZero();

        // The database is the source of truth, not our counters.
        Long rows = jdbc.queryForObject(
                "SELECT count(*) FROM bookings WHERE status IN ('HOLD','CONFIRMED')", Long.class);
        assertThat(rows).as("one row in the database, whatever the threads believed").isEqualTo(1L);
    }

    @Test
    @DisplayName("after the winner cancels, the slot can be booked again")
    void cancel_thenRebook_succeeds() {
        var winner = bookingService.create(
                new BookingRequest(court.getId(), slotStart, slotEnd), contenders.get(0));

        bookingService.cancel(winner.getId(), contenders.get(0));

        var second = bookingService.create(
                new BookingRequest(court.getId(), slotStart, slotEnd), contenders.get(1));

        assertThat(second.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        // Two rows share the slot: one CANCELLED (history), one CONFIRMED (live).
        assertThat(bookingRepository.count()).isEqualTo(2);
    }
}
