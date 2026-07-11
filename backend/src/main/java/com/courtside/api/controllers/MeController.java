package com.courtside.api.controllers;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.courtside.api.dtos.MeResponse;
import com.courtside.api.entities.User;

@RestController
@RequestMapping("/api/v1")
public class MeController {

    /**
     * Spring injects the Authentication that the JWT filter stored in the
     * SecurityContext. If the request had no valid token, Security answers
     * 401 before this method is ever reached.
     */
    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return new MeResponse(
            user.getEmail(),
            user.getFullName(),
            user.getRole().name()
        );
    }
}
