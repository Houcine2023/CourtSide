package com.courtside.api.controllers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.courtside.api.dtos.BookingRequest;
import com.courtside.api.dtos.BookingResponse;
import com.courtside.api.dtos.PageResponse;
import com.courtside.api.entities.Booking;
import com.courtside.api.entities.User;
import com.courtside.api.services.BookingService;

import jakarta.validation.Valid;

/**
 * All endpoints here require authentication (the catch-all rule in SecurityConfig):
 * a booking always belongs to somebody. No @PreAuthorize is needed because every
 * ROLE may book — the finer rules (is it yours?) live in the service.
 */
@RestController
@RequestMapping("/api/v1")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse book(
            @Valid @RequestBody BookingRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return BookingResponse.from(bookingService.create(request, currentUser));
    }

    /**
     * Reserve a slot WITHOUT confirming it: the classic payment flow. The slot is
     * blocked for other users (the exclusion constraint counts HOLD as occupying)
     * and is released automatically if /confirm never arrives.
     */
    @PostMapping("/bookings/hold")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse hold(
            @Valid @RequestBody BookingRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        return BookingResponse.from(bookingService.createHold(request, currentUser));
    }

    /** Payment succeeded (simulated) → HOLD becomes CONFIRMED. */
    @PostMapping("/bookings/{id}/confirm")
    public BookingResponse confirm(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        return BookingResponse.from(bookingService.confirmHold(id, currentUser));
    }

    @GetMapping("/me/bookings")
    public PageResponse<BookingResponse> myBookings(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "false") boolean upcomingOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<Booking> result = bookingService.listMine(
                currentUser, upcomingOnly, PageRequest.of(Math.max(page, 0), safeSize));
        return PageResponse.from(result, BookingResponse::from);
    }

    @GetMapping("/bookings/{id}")
    public BookingResponse getOne(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        return BookingResponse.from(bookingService.getById(id, currentUser));
    }

    /**
     * DELETE performs a cancellation (status change), not a row deletion — and it
     * returns the updated booking so the client can show the new status.
     */
    @DeleteMapping("/bookings/{id}")
    public BookingResponse cancel(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        return BookingResponse.from(bookingService.cancel(id, currentUser));
    }
}
