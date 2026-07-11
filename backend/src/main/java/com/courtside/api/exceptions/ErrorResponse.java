package com.courtside.api.exceptions;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The single error shape of the whole API. fieldErrors is only present for
 * validation failures; @JsonInclude hides it from the JSON when null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    int status,
    String error,
    String message,
    Map<String, String> fieldErrors
) {
    /** Convenience constructor for errors without field details. */
    public ErrorResponse(int status, String error, String message) {
        this(status, error, message, null);
    }
}
