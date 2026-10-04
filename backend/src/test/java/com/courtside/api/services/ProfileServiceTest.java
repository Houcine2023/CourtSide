package com.courtside.api.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.courtside.api.dtos.ChangePasswordRequest;
import com.courtside.api.dtos.MeResponse;
import com.courtside.api.dtos.UpdateProfileRequest;
import com.courtside.api.entities.Role;
import com.courtside.api.entities.User;
import com.courtside.api.repositories.UserRepository;

/**
 * The profile screen used to claim success without ever calling the API, so these
 * cover the rules that make a change actually take effect: the name is persisted,
 * the password is re-verified before it is replaced, and every session is revoked
 * so a stolen token cannot outlive the old password.
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private ProfileService profileService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7L);
        user.setEmail("ahmed@test.tn");
        user.setFullName("Ahmed Mansouri");
        user.setPasswordHash("old-hash");
        user.setRole(Role.MEMBER);
    }

    @Test
    @DisplayName("updateProfile persists the new name and returns the updated identity")
    void updateProfile_persistsTrimmedName() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        MeResponse result = profileService.updateProfile(user, new UpdateProfileRequest("  Ahmed B  "));

        // Trimmed, not stored padded, so the name cannot drift into a variant.
        assertThat(user.getFullName()).isEqualTo("Ahmed B");
        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.fullName()).isEqualTo("Ahmed B");
        assertThat(result.role()).isEqualTo("MEMBER");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("updateProfile never exposes the password hash")
    void updateProfile_keepsIdentityFieldsOnly() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        MeResponse result = profileService.updateProfile(user, new UpdateProfileRequest("Ahmed B"));

        assertThat(result.toString()).doesNotContain("old-hash");
    }

    @Test
    @DisplayName("changePassword rejects a wrong current password without saving")
    void changePassword_wrongCurrentPassword_isRejected() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(encoder.matches("wrong", "old-hash")).thenReturn(false);

        assertThatThrownBy(() ->
                profileService.changePassword(user, new ChangePasswordRequest("wrong", "newpassword")))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    @DisplayName("changePassword stores a hash and revokes every session on success")
    void changePassword_valid_rehashesAndRevokesSessions() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(encoder.matches("old-secret", "old-hash")).thenReturn(true);
        when(encoder.matches("new-secret", "old-hash")).thenReturn(false);
        when(encoder.encode("new-secret")).thenReturn("new-hash");

        profileService.changePassword(user, new ChangePasswordRequest("old-secret", "new-secret"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(userRepository).save(user);
        // Without this, a refresh token captured before the change stays valid.
        verify(refreshTokenService).revokeAllForUser(user);
    }

    @Test
    @DisplayName("changePassword refuses a new password identical to the current one")
    void changePassword_sameAsCurrent_isRejected() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(encoder.matches("old-secret", "old-hash")).thenReturn(true);

        assertThatThrownBy(() ->
                profileService.changePassword(user, new ChangePasswordRequest("old-secret", "old-secret")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different");

        verify(userRepository, never()).save(any());
    }
}
