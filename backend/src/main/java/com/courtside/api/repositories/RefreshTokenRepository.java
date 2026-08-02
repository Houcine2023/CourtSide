package com.courtside.api.repositories;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.courtside.api.entities.RefreshToken;
import com.courtside.api.entities.User;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken,Long> {

    /** Plain lookup: enough when we only need to flip `revoked` (logout). */
    Optional<RefreshToken> findByTokenHash(String hash);

    /**
     * Same lookup, but loads the User in the SAME query.
     * Without the JOIN FETCH, `token.getUser()` would trigger a second SELECT
     * (lazy loading), or fail outright if the persistence session is already closed.
     * This is the standard cure for the N+1 problem: ask for what you need, once.
     */
    @Query("select rt from RefreshToken rt join fetch rt.user where rt.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHashWithUser(String hash);

    /** Used by reuse detection: find every live token of a compromised user. */
    List<RefreshToken> findAllByUserAndRevokedFalse(User user);

    /**
     * Housekeeping (see RefreshTokenCleanupJob). Derived DELETE queries must run in a
     * transaction — the caller provides it. Returns how many rows were removed.
     */
    long deleteByExpiresAtBefore(OffsetDateTime cutoff);

}
