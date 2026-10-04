package com.courtside.api.services;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.courtside.api.dtos.ChangePasswordRequest;
import com.courtside.api.dtos.MeResponse;
import com.courtside.api.dtos.UpdateProfileRequest;
import com.courtside.api.entities.User;
import com.courtside.api.exceptions.NotFoundException;
import com.courtside.api.repositories.UserRepository;

/**
 * Profile and password changes for the signed-in user.
 *
 * The endpoints existed only in the UI, which reported success without ever
 * calling the API. These implementations make the buttons actually persist.
 */
@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final RefreshTokenService refreshTokenService;

    public ProfileService(UserRepository userRepository, PasswordEncoder encoder,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.encoder = encoder;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public MeResponse updateProfile(User user, UpdateProfileRequest request) {
        User managed = requireUser(user.getId());

        // Trim first: " Ahmed " and "Ahmed" are the same person, and storing the
        // padded value would make the name look like a distinct account in the UI.
        managed.setFullName(request.fullName().trim());
        userRepository.save(managed);

        return new MeResponse(
                managed.getId(),
                managed.getEmail(),
                managed.getFullName(),
                managed.getRole().name()
        );
    }

    /**
     * Changes the password after re-verifying the current one, then revokes every
     * refresh token so any other session stops working. Without the revocation, a
     * stolen refresh token would keep minting access tokens under the old password.
     */
    @Transactional
    public void changePassword(User user, ChangePasswordRequest request) {
        User managed = requireUser(user.getId());

        if (!encoder.matches(request.currentPassword(), managed.getPasswordHash())) {
            // Same message as a wrong password elsewhere: never confirm whether
            // the account exists or the password was merely wrong.
            throw new BadCredentialsException("Current password is incorrect.");
        }

        if (encoder.matches(request.newPassword(), managed.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from the current one.");
        }

        managed.setPasswordHash(encoder.encode(request.newPassword()));
        userRepository.save(managed);
        refreshTokenService.revokeAllForUser(managed);
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found."));
    }
}
