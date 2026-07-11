package com.courtside.api.exceptions;

public record ErrorResponse(
    int status,
    String error,
    String message
) {}
