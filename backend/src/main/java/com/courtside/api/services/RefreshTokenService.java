package com.courtside.api.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.courtside.api.entities.RefreshToken;
import com.courtside.api.entities.User;
import com.courtside.api.repositories.RefreshTokenRepository;

/**
 * Everything that concerns refresh tokens: creation, verification, rotation, revocation.
 *
 * The golden rule of this class: the RAW token exists only in memory and in the HTTP
 * response. What lands in the database is always its SHA-256 hash.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    /**
     * SecureRandom, never `new Random()` / `Math.random()`.
     * Ordinary generators are deterministic: knowing a few outputs lets an attacker
     * predict the next ones. SecureRandom draws from the OS entropy pool.
     * One shared instance is fine — it is thread-safe.
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 32 bytes = 256 bits of entropy: unguessable by brute force, ever. */
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;

    /** Lifetime in days, configurable — durations must never be hardcoded. */
    @Value("${app.refresh.expiration-days:7}")
    private long expirationDays;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Creates a new refresh token for this user, stores its hash, and returns
     * the RAW token — the only moment it is ever readable. Store it client-side;
     * the server can never show it again.
     */
    @Transactional
    public String issue(User user) {
        String rawToken = generateRawToken();

        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(sha256Hex(rawToken));
        entity.setExpiresAt(OffsetDateTime.now().plusDays(expirationDays));
        entity.setRevoked(false);

        refreshTokenRepository.save(entity);

        return rawToken;
    }

    /**
     * Verifies a refresh token and "uses it up" (rotation): the row is revoked so the
     * same token can never be presented twice. Returns the owner, so the caller can
     * mint a fresh pair of tokens.
     *
     * Failure cases ALL raise the same generic BadCredentialsException -> 401.
     * The client must never learn whether the token was unknown, replayed, or expired.
     *
     * noRollbackFor: by default Spring rolls back the transaction on any RuntimeException.
     * That would silently undo the revocations we perform right before throwing —
     * exactly the security action we care about. This annotation says:
     * "commit my writes, then let the exception fly."
     */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public User consume(String rawToken) {
        String hash = sha256Hex(rawToken);

        RefreshToken token = refreshTokenRepository.findByTokenHashWithUser(hash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        // --- REUSE DETECTION -------------------------------------------------
        // This token was already used (rotation revoked it) yet someone is presenting
        // it again. Two possible worlds: an attacker stole it, or the legitimate user
        // replays an old one. We cannot tell them apart, so we assume the worst and
        // kill every live token of this user: the thief AND the victim must log in again.
        if (token.isRevoked()) {
            log.warn("Refresh token reuse detected for user id={} — revoking all sessions",
                    token.getUser().getId());
            revokeAllForUser(token.getUser());
            throw new BadCredentialsException("Invalid refresh token");
        }

        if (token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            // Expired: revoke it so it can never be replayed, then reject.
            token.setRevoked(true);
            throw new BadCredentialsException("Invalid refresh token");
        }

        // Valid: consume it. The entity is managed inside this transaction, so this
        // change is flushed automatically (Hibernate dirty checking) — no save() needed.
        token.setRevoked(true);

        return token.getUser();
    }

    /** Logout: revoke this one token. Unknown tokens are ignored on purpose (see below). */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(sha256Hex(rawToken))
                .ifPresent(token -> token.setRevoked(true));
        // No exception when the token is unknown: logout is idempotent, and answering
        // "that token does not exist" would turn this endpoint into an oracle for
        // testing whether a stolen token is still alive.
    }

    @Transactional
    public void revokeAllForUser(User user) {
        List<RefreshToken> live = refreshTokenRepository.findAllByUserAndRevokedFalse(user);
        live.forEach(t -> t.setRevoked(true));
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        // URL-safe Base64 so the token can travel in JSON/headers/URLs untouched;
        // no padding ('=') to avoid encoding surprises.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256, hex-encoded (64 chars — matches VARCHAR(64) in the schema).
     *
     * Why SHA-256 here but BCrypt for passwords? BCrypt is deliberately SLOW to make
     * brute-forcing guessable human passwords impractical. A 256-bit random token
     * cannot be guessed at all, so slowness buys nothing and would cost latency on
     * every refresh. Fast hash + huge entropy is the right pairing.
     */
    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present in every JVM; if it is missing the platform
            // is broken beyond recovery, so fail loudly instead of pretending.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
