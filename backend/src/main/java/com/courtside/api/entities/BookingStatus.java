package com.courtside.api.entities;

/**
 * Lifecycle of a booking. Must stay in sync with the CHECK constraint on bookings.status.
 *
 * Only HOLD and CONFIRMED are guarded by the no_overlapping_bookings exclusion
 * constraint (see its WHERE clause): a CANCELLED booking may overlap freely, which is
 * what lets a slot be re-booked after a cancellation while keeping the history.
 */
public enum BookingStatus {
    /** Reserved while a payment completes; released automatically if it does not. */
    HOLD,
    CONFIRMED,
    CANCELLED,
    NO_SHOW
}
