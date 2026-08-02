package com.courtside.api.repositories;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.courtside.api.entities.Booking;
import com.courtside.api.entities.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /**
     * Active bookings of one court that OVERLAP a time window.
     *
     * The overlap test is the classic one: two ranges [a1,a2) and [b1,b2) overlap when
     * a1 < b2 AND a2 > b1. Strict inequalities mean touching ranges (10:00-11:30 and
     * 11:30-13:00) do NOT overlap — exactly the half-open semantics of the database's
     * tstzrange in the exclusion constraint. The two definitions must agree, otherwise
     * Java would offer a slot that Postgres then rejects.
     */
    @Query("""
            select b from Booking b
            where b.court.id = :courtId
              and b.status in :activeStatuses
              and b.startTime < :windowEnd
              and b.endTime > :windowStart
            order by b.startTime
            """)
    List<Booking> findOverlapping(Long courtId,
                                  OffsetDateTime windowStart,
                                  OffsetDateTime windowEnd,
                                  List<BookingStatus> activeStatuses);

    /** "My bookings", newest first, paginated. Court and club fetched for the response. */
    @Query(value = """
            select b from Booking b
            join fetch b.court c
            join fetch c.club
            where b.user.id = :userId
              and (:upcomingOnly = false or b.startTime >= :now)
            order by b.startTime desc
            """,
           countQuery = """
            select count(b) from Booking b
            where b.user.id = :userId
              and (:upcomingOnly = false or b.startTime >= :now)
            """)
    Page<Booking> findMine(Long userId, boolean upcomingOnly, OffsetDateTime now, Pageable pageable);

    /** Single booking with everything the ownership check and the response need. */
    @Query("""
            select b from Booking b
            join fetch b.court c
            join fetch c.club club
            left join fetch club.manager
            join fetch b.user
            where b.id = :id
            """)
    Optional<Booking> findByIdDetailed(Long id);
}
