package com.courtside.api.exceptions;

/** Thrown when a requested resource does not exist. Mapped to 404 by the handler. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    /** e.g. new NotFoundException("Club", 42) -> "Club 42 not found" */
    public NotFoundException(String resource, Object id) {
        super(resource + " " + id + " not found");
    }
}
