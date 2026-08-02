package com.courtside.api.exceptions;

/**
 * The requested slot is taken. Mapped to 409 Conflict: the request is valid, but the
 * current state of the world refuses it — and it might succeed later (after a
 * cancellation), which is exactly what 409 means.
 */
public class SlotUnavailableException extends RuntimeException {

    public SlotUnavailableException(String message) {
        super(message);
    }
}
