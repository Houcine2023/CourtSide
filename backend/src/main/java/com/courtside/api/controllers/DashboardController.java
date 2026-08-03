package com.courtside.api.controllers;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.courtside.api.dtos.DashboardResponse;
import com.courtside.api.entities.User;
import com.courtside.api.services.DashboardService;

@RestController
@RequestMapping("/api/v1/clubs/{clubId}")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Revenue and occupancy for one club. Staff only — and the service additionally
     * verifies that a MANAGER owns THIS club, so a manager cannot read a competitor's
     * numbers by changing the id in the URL (a classic IDOR vulnerability).
     *
     * Defaults to the last 30 days when no range is given.
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public DashboardResponse dashboard(
            @PathVariable Long clubId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal User currentUser
    ) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate begin = from == null ? end.minusDays(30) : from;
        return dashboardService.build(clubId, begin, end, currentUser);
    }
}
