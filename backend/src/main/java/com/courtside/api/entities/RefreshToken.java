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
// REQUIRED: without it Hibernate derives the table name from the CLASS name
// (RefreshToken -> "refresh_token", singular) and startup fails, because our
// Flyway schema created "refresh_tokens". The entity must mirror the migration.
@Table(name = "refresh_tokens")
@Getter
@Setter
public class RefreshToken {

    // Long, not long: a primitive cannot be null, so an unsaved entity would carry
    // id = 0 — indistinguishable from a real id. Wrapper types make "not persisted
    // yet" explicit.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The field is called `user` (the OBJECT it points to), not `user_id`.
     * Two reasons:
     *  1. Lombok generates getUser()/setUser() from the field name — naming it
     *     user_id gives getUser_id(), which is why the whole service stopped compiling.
     *  2. JPQL navigates objects, not columns: "join fetch rt.user" needs a `user` property.
     * The COLUMN name lives in @JoinColumn — that is the bridge between the two worlds.
     * (Without @JoinColumn, Hibernate would invent `user_id_id`: field name + referenced PK.)
     *
     * FetchType.LAZY: @ManyToOne is EAGER by default, so every token lookup would
     * silently fire a second query — the seed of the N+1 problem. When we DO need
     * the user, we ask explicitly with a JOIN FETCH query.
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

    // @CreationTimestamp: Hibernate fills this on INSERT. Without it the field stays
    // null and Postgres rejects the row (created_at is NOT NULL) — a runtime failure
    // the compiler can never warn you about.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
