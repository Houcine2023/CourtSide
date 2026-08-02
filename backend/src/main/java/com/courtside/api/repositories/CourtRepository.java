package com.courtside.api.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.courtside.api.entities.Court;

public interface CourtRepository extends JpaRepository<Court, Long> {

    /** Courts of one club. `OrderByName` is part of the derived-query grammar. */
    List<Court> findByClubIdAndActiveTrueOrderByName(Long clubId);

    List<Court> findByClubIdOrderByName(Long clubId);

    /**
     * Loads a court together with its club AND the club's manager in ONE query.
     * The ownership check needs court -> club -> manager; without the fetch joins
     * that is three SELECTs (and a LazyInitializationException risk once the
     * transaction closes).
     */
    @Query("""
            select c from Court c
            join fetch c.club club
            left join fetch club.manager
            where c.id = :id
            """)
    Optional<Court> findByIdWithClubAndManager(Long id);
}
