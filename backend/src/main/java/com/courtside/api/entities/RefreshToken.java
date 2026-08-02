package com.courtside.api.entities;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One row = one issued refresh token.
 *
 * We store the SHA-256 HASH of the token, never the token itself: if this table
 * ever leaks, the attacker holds useless hashes instead of live sessions.
 * Same reasoning as password hashing.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * FetchType.LAZY: loading a token must NOT automatically drag the whole User
     * row along. @ManyToOne is EAGER by default, so every token lookup would
     * silently fire a second query — the seed of the N+1 problem.
     * When we DO need the user, we ask for it explicitly with a JOIN FETCH query.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** SHA-256 of the raw token, hex-encoded => always exactly 64 characters. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    /**
     * Revoked tokens are kept, not deleted: that is what makes reuse detection
     * possible. A deleted row is indistinguishable from a token that never
     * existed; a revoked row tells us "this one was already used" — a theft signal.
     */
    @Column(nullable = false)
    private boolean revoked = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
