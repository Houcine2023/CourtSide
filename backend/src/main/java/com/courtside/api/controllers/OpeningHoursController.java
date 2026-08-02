package com.courtside.api.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.courtside.api.dtos.OpeningHoursRequest;
import com.courtside.api.dtos.OpeningHoursResponse;
import com.courtside.api.entities.User;
import com.courtside.api.services.OpeningHoursService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/clubs/{clubId}/opening-hours")
public class OpeningHoursController {

    private final OpeningHoursService openingHoursService;

    public OpeningHoursController(OpeningHoursService openingHoursService) {
        this.openingHoursService = openingHoursService;
    }

    /** Public: visitors need to know when the club is open. */
    @GetMapping
    public List<OpeningHoursResponse> list(@PathVariable Long clubId) {
        return openingHoursService.listByClub(clubId).stream()
                .map(OpeningHoursResponse::from)
                .toList();
    }

    /**
     * PUT, not POST: setting Monday's hours twice must leave ONE row, not two.
     * That is the definition of idempotent — and the service upserts accordingly.
     */
    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public OpeningHoursResponse setDay(
            @PathVariable Long clubId,
            @Valid @RequestBody OpeningHoursRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return OpeningHoursResponse.from(openingHoursService.setForDay(clubId, request, currentUser));
    }

    @DeleteMapping("/{dayOfWeek}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public void deleteDay(
            @PathVariable Long clubId,
            @PathVariable Integer dayOfWeek,
            @AuthenticationPrincipal User currentUser
    ) {
        openingHoursService.deleteDay(clubId, dayOfWeek, currentUser);
    }
}
