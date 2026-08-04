package com.courtside.api;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;

import com.courtside.api.entities.Booking;
import com.courtside.api.entities.BookingStatus;
import com.courtside.api.entities.Club;
import com.courtside.api.entities.Court;
import com.courtside.api.entities.OpeningHours;
import com.courtside.api.entities.Role;
import com.courtside.api.entities.Sport;
import com.courtside.api.entities.User;

/**
 * Object builders shared by the tests.
 *
 * Why a fixtures class rather than building entities inline in every test?
 * A test should read as "given a court, when I book outside opening hours, then 422" —
 * fifteen lines of setter calls bury that sentence. Each helper takes ONLY the fields
 * the tests actually vary; everything else gets a sane default.
 */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static User user(Long id, String email, Role role) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setFullName(email);
        u.setPasswordHash("$2a$10$hash");
        u.setRole(role);
        return u;
    }

    public static User member(Long id) {
        return user(id, "member" + id + "@test.tn", Role.MEMBER);
    }

    public static User manager(Long id) {
        return user(id, "manager" + id + "@test.tn", Role.MANAGER);
    }

    public static User admin(Long id) {
        return user(id, "admin" + id + "@test.tn", Role.ADMIN);
    }

    public static Club club(Long id, User manager) {
        Club c = new Club();
        c.setId(id);
        c.setName("Test Club " + id);
        c.setCity("Tunis");
        c.setAddress("Somewhere");
        c.setManager(manager);
        return c;
    }

    /** Court with 90-minute slots at 60.00 — the shape used across the tests. */
    public static Court court(Long id, Club club) {
        Court c = new Court();
        c.setId(id);
        c.setClub(club);
        c.setName("Court " + id);
        c.setSport(Sport.PADEL);
        c.setSlotMinutes(90);
        c.setPricePerSlot(new BigDecimal("60.00"));
        c.setActive(true);
        return c;
    }

    /** Opening hours 08:00–22:00 for the given ISO weekday (1 = Monday). */
    public static OpeningHours openingHours(Club club, int dayOfWeek) {
        return openingHours(club, dayOfWeek, LocalTime.of(8, 0), LocalTime.of(22, 0));
    }

    public static OpeningHours openingHours(Club club, int dayOfWeek, LocalTime opens, LocalTime closes) {
        OpeningHours oh = new OpeningHours();
        oh.setId(1L);
        oh.setClub(club);
        oh.setDayOfWeek(dayOfWeek);
        oh.setOpens(opens);
        oh.setCloses(closes);
        return oh;
    }

    public static Booking booking(Long id, Court court, User user,
                                  OffsetDateTime start, OffsetDateTime end,
                                  BookingStatus status) {
        Booking b = new Booking();
        b.setId(id);
        b.setCourt(court);
        b.setUser(user);
        b.setStartTime(start);
        b.setEndTime(end);
        b.setStatus(status);
        b.setPrice(court.getPricePerSlot());
        return b;
    }
}
