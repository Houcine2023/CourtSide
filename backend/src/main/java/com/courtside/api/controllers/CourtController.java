package com.courtside.api.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.courtside.api.dtos.CourtRequest;
import com.courtside.api.dtos.CourtResponse;
import com.courtside.api.entities.User;
import com.courtside.api.services.CourtService;

import jakarta.validation.Valid;

/**
 * Courts live under their club: /clubs/{clubId}/courts for collection operations,
 * /courts/{id} for a single one. Nesting expresses ownership; flat paths keep
 * item URLs short and stable.
 */
@RestController
@RequestMapping("/api/v1")
public class CourtController {

    private final CourtService courtService;

    public CourtController(CourtService courtService) {
        this.courtService = courtService;
    }

    @GetMapping("/clubs/{clubId}/courts")
    public List<CourtResponse> listByClub(
            @PathVariable Long clubId,
            // Only staff should pass includeInactive=true; harmless if a member does,
            // but the flag exists for management screens.
            @RequestParam(defaultValue = "false") boolean includeInactive
    ) {
        return courtService.listByClub(clubId, includeInactive)
                .stream()
                .map(CourtResponse::from)
                .toList();
    }

    @GetMapping("/courts/{id}")
    public CourtResponse getOne(@PathVariable Long id) {
        return CourtResponse.from(courtService.getById(id));
    }

    @PostMapping("/clubs/{clubId}/courts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CourtResponse create(
            @PathVariable Long clubId,
            @Valid @RequestBody CourtRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return CourtResponse.from(courtService.create(clubId, request, currentUser));
    }

    @PutMapping("/courts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CourtResponse update(
            @PathVariable Long id,
            @Valid @RequestBody CourtRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return CourtResponse.from(courtService.update(id, request, currentUser));
    }

    /** Deactivation, not deletion — see CourtService.deactivate. */
    @DeleteMapping("/courts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public void deactivate(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        courtService.deactivate(id, currentUser);
    }
}
