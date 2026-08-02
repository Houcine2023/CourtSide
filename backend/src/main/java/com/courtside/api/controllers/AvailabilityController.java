package com.courtside.api.controllers;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.courtside.api.dtos.AvailabilityResponse;
import com.courtside.api.services.AvailabilityService;

@RestController
@RequestMapping("/api/v1/courts/{courtId}")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    /**
     * Public availability grid: people must be able to see free slots before signing up.
     *
     * @DateTimeFormat(ISO.DATE) parses ?date=2026-08-15. Without it Spring cannot turn
     * the string into a LocalDate and answers 400.
     */
    @GetMapping("/availability")
    public AvailabilityResponse availability(
            @PathVariable Long courtId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return availabilityService.getForDay(courtId, date == null ? LocalDate.now() : date);
    }
}
