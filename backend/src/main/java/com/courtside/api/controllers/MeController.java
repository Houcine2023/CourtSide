package com.courtside.api.controllers;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.courtside.api.dtos.ChangePasswordRequest;
import com.courtside.api.dtos.MeResponse;
import com.courtside.api.dtos.UpdateProfileRequest;
import com.courtside.api.entities.User;
import com.courtside.api.services.ProfileService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class MeController {

    private final ProfileService profileService;

    public MeController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * Spring injects the Authentication that the JWT filter stored in the
     * SecurityContext. If the request had no valid token, Security answers
     * 401 before this method is ever reached.
     */
    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return new MeResponse(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getRole().name()
        );
    }

    @PutMapping("/me")
    public MeResponse updateProfile(Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.updateProfile((User) authentication.getPrincipal(), request);
    }

    /**
     * Changing the password invalidates all sessions, so the response tells the
     * client to sign in again rather than leaving it on a page whose tokens are
     * now dead.
     */
    @PostMapping("/me/password")
    public PasswordChangedResponse changePassword(Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        profileService.changePassword((User) authentication.getPrincipal(), request);
        return new PasswordChangedResponse(true);
    }

    public record PasswordChangedResponse(boolean sessionRevoked) {
    }
}
