package com.courtside.api.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.courtside.api.dtos.AuthResponse;
import com.courtside.api.dtos.LoginRequest;
import com.courtside.api.dtos.RefreshRequest;
import com.courtside.api.dtos.RegisterRequest;
import com.courtside.api.services.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(
        @Valid
        @RequestBody
        RegisterRequest request
    ){
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(
            @Valid
            @RequestBody
            LoginRequest request
    ){
        return authService.login(request);
    }

    /**
     * Exchange a refresh token for a new pair.
     *
     * This endpoint is intentionally reachable WITHOUT an access token: its whole
     * purpose is to be called once the access token has expired. Requiring a valid
     * access token here would create a deadlock — you could only refresh while you
     * did not need to. The refresh token itself is the credential.
     */
    @PostMapping("/refresh")
    public AuthResponse refresh(
            @Valid
            @RequestBody
            RefreshRequest request
    ){
        return authService.refresh(request);
    }

    /** 204 No Content: the action succeeded and there is nothing meaningful to return. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @Valid
            @RequestBody
            RefreshRequest request
    ){
        authService.logout(request);
    }
}
