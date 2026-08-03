package com.courtside.api.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.courtside.api.dtos.WaitlistRequest;
import com.courtside.api.dtos.WaitlistResponse;
import com.courtside.api.entities.User;
import com.courtside.api.services.WaitlistService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class WaitlistController {

    private final WaitlistService waitlistService;

    public WaitlistController(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    /** Any authenticated user may queue for a slot. No role needed. */
    @PostMapping("/waitlist")
    @ResponseStatus(HttpStatus.CREATED)
    public WaitlistResponse join(
            @Valid @RequestBody WaitlistRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return WaitlistResponse.from(waitlistService.join(request, currentUser));
    }

    @GetMapping("/me/waitlist")
    public List<WaitlistResponse> mine(@AuthenticationPrincipal User currentUser) {
        return waitlistService.listMine(currentUser).stream()
                .map(WaitlistResponse::from)
                .toList();
    }

    @DeleteMapping("/waitlist/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        waitlistService.leave(id, currentUser);
    }
}
