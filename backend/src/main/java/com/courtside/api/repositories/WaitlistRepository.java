package com.courtside.api.repositories;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.courtside.api.entities.WaitlistEntry;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    /**
     * Everyone still waiting for exactly this slot, OLDEST FIRST.
     * The ordering is the whole fairness guarantee of the feature.
     */
    @Query("""
            select w from WaitlistEntry w
            join fetch w.user
            join fetch w.court c
            join fetch c.club
            where w.court.id = :courtId
              and w.startTime = :startTime
              and w.endTime = :endTime
              and w.active = true
            order by w.createdAt asc
            """)
    List<WaitlistEntry> findWaiting(Long courtId, OffsetDateTime startTime, OffsetDateTime endTime);

    /** Guards the "already on this waitlist" case before hitting the unique index. */
    Optional<WaitlistEntry> findByCourtIdAndUserIdAndStartTimeAndEndTimeAndActiveTrue(
            Long courtId, Long userId, OffsetDateTime startTime, OffsetDateTime endTime);

    @Query("""
            select w from WaitlistEntry w
            join fetch w.court c
            join fetch c.club
            where w.user.id = :userId and w.active = true
            order by w.startTime asc
            """)
    List<WaitlistEntry> findMine(Long userId);

    @Query("select w from WaitlistEntry w join fetch w.user where w.id = :id")
    Optional<WaitlistEntry> findByIdWithUser(Long id);

    /** Housekeeping: entries whose slot is now in the past are dead weight. */
    @Query("select w from WaitlistEntry w where w.active = true and w.startTime < :now")
    List<WaitlistEntry> findStale(OffsetDateTime now);
}
