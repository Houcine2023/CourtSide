package com.courtside.api.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.courtside.api.entities.Club;

public interface ClubRepository extends JpaRepository<Club, Long> {

    /**
     * Search by name pattern and/or city.
     *
     * Note there is no ":q is null" here. A NULL parameter has no type, and Postgres
     * refuses to guess: Hibernate sends it as `bytea` and you get
     * "function lower(bytea) does not exist". The service therefore normalises the
     * inputs (null -> "%" for the pattern, null -> "" for the city) so the query
     * always receives real strings. Rule of thumb: keep NULLs out of JPQL parameters.
     *
     * `left join fetch c.manager` loads the manager in the same query — the response
     * DTO needs the manager's name, and it is mapped AFTER the transaction closes
     * (open-in-view is off), so a lazy proxy would explode with
     * LazyInitializationException. LEFT because a club may have no manager.
     * This is safe with pagination because manager is single-valued (@ManyToOne);
     * fetch-joining a COLLECTION with a Pageable would force in-memory paging.
     */
    @Query(value = """
            select c from Club c
            left join fetch c.manager
            where lower(c.name) like lower(:namePattern)
              and (:city = '' or lower(c.city) = lower(:city))
            """,
           countQuery = """
            select count(c) from Club c
            where lower(c.name) like lower(:namePattern)
              and (:city = '' or lower(c.city) = lower(:city))
            """)
    Page<Club> search(String namePattern, String city, Pageable pageable);

    /** Single club with its manager pre-loaded, for the same DTO-mapping reason. */
    @Query("select c from Club c left join fetch c.manager where c.id = :id")
    Optional<Club> findByIdWithManager(Long id);
}
