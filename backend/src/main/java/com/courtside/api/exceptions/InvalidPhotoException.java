package com.courtside.api.exceptions;

/**
 * An upload the server understood but will not accept: no file, an empty file,
 * or a media type outside the whitelist.
 *
 * 400 rather than 422 — the payload itself is the problem, not the state of the
 * world. Mapped to 400 by {@link GlobalExceptionHandler}.
 */
public class InvalidPhotoException extends RuntimeException {

    public InvalidPhotoException(String message) {
        super(message);
    }
}
