package com.courtside.api.services;

import static com.courtside.api.TestFixtures.member;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

import com.courtside.api.entities.RefreshToken;
import com.courtside.api.entities.User;
import com.courtside.api.repositories.RefreshTokenRepository;

/**
 * Refresh-token security: hashing, rotation and reuse detection.
 *
 * These are the tests that would catch the most dangerous regressions in the whole
 * codebase — a refresh token stored in clear, or a replayed token still working.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService — hashing, rotation, reuse detection")
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User alice;

    @BeforeEach
    void setUp() {
        alice = member(1L);
        ReflectionTestUtils.setField(refreshTokenService, "expirationDays", 7L);
    }

    private RefreshToken storedToken(User owner, boolean revoked, OffsetDateTime expiresAt) {
        RefreshToken t = new RefreshToken();
        t.setId(100L);
        t.setUser(owner);
        t.setTokenHash("irrelevant-the-service-computes-its-own");
        t.setRevoked(revoked);
        t.setExpiresAt(expiresAt);
        return t;
    }

    @Test
    @DisplayName("the raw token is returned but only its SHA-256 hash is stored")
    void issue_storesHashNeverTheRawToken() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        String rawToken = refreshTokenService.issue(alice);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());

        // If this ever fails, a database dump becomes a pile of live sessions.
        assertThat(saved.getValue().getTokenHash())
                .isNotEqualTo(rawToken)
                .hasSize(64)                      // SHA-256 in hex
                .matches("[0-9a-f]{64}");
        assertThat(rawToken).hasSizeGreaterThan(32); // 32 random bytes, Base64url
        assertThat(saved.getValue().isRevoked()).isFalse();
        assertThat(saved.getValue().getExpiresAt()).isAfter(OffsetDateTime.now().plusDays(6));
    }

    @Test
    @DisplayName("two issued tokens are never the same")
    void issue_producesUniqueTokens() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(refreshTokenService.issue(alice))
                .isNotEqualTo(refreshTokenService.issue(alice));
    }

    @Test
    @DisplayName("consuming a valid token revokes it and returns the owner (rotation)")
    void consume_whenValid_revokesAndReturnsOwner() {
        RefreshToken stored = storedToken(alice, false, OffsetDateTime.now().plusDays(3));
        when(refreshTokenRepository.findByTokenHashWithUser(anyString())).thenReturn(Optional.of(stored));

        User owner = refreshTokenService.consume("some-raw-token");

        assertThat(owner).isSameAs(alice);
        // Single-use: this is what makes reuse detectable at all.
        assertThat(stored.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("an unknown token is rejected")
    void consume_whenUnknown_throws() {
        when(refreshTokenRepository.findByTokenHashWithUser(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.consume("never-issued"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("REUSE of a revoked token revokes every session of that user")
    void consume_whenAlreadyRevoked_revokesWholeFamily() {
        RefreshToken alreadyUsed = storedToken(alice, true, OffsetDateTime.now().plusDays(3));
        RefreshToken live1 = storedToken(alice, false, OffsetDateTime.now().plusDays(3));
        RefreshToken live2 = storedToken(alice, false, OffsetDateTime.now().plusDays(3));
        when(refreshTokenRepository.findByTokenHashWithUser(anyString()))
                .thenReturn(Optional.of(alreadyUsed));
        when(refreshTokenRepository.findAllByUserAndRevokedFalse(alice))
                .thenReturn(List.of(live1, live2));

        assertThatThrownBy(() -> refreshTokenService.consume("replayed-token"))
                .isInstanceOf(BadCredentialsException.class);

        // Somebody replayed a used token: we cannot tell thief from victim, so both
        // are logged out. This is the OAuth 2 BCP response to token theft.
        assertThat(live1.isRevoked()).isTrue();
        assertThat(live2.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("an expired token is rejected and revoked")
    void consume_whenExpired_revokesAndThrows() {
        RefreshToken expired = storedToken(alice, false, OffsetDateTime.now().minusDays(1));
        when(refreshTokenRepository.findByTokenHashWithUser(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.consume("stale-token"))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(expired.isRevoked()).isTrue();
        // No family wipe: an expired token is normal ageing, not evidence of theft.
        verify(refreshTokenRepository, never()).findAllByUserAndRevokedFalse(any());
    }

    @Test
    @DisplayName("logout revokes the token, and stays silent about unknown ones")
    void revoke_isIdempotentAndSilent() {
        RefreshToken stored = storedToken(alice, false, OffsetDateTime.now().plusDays(3));
        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(stored))
                .thenReturn(Optional.empty());

        refreshTokenService.revoke("raw");
        assertThat(stored.isRevoked()).isTrue();

        // Second call, token unknown: no exception — otherwise logout becomes an
        // oracle for testing whether a stolen token is still alive.
        refreshTokenService.revoke("raw");
    }
}
