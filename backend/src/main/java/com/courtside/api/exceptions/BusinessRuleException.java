package com.courtside.api.exceptions;

/**
 * A request that is well-formed but breaks a business rule
 * (booking in the past, outside opening hours, cancelling too late...).
 *
 * Distinct from validation errors (400 — the payload itself is malformed) and from
 * conflicts (409 — the state of the world blocks you). Mapped to 422 Unprocessable
 * Content: "I understood you, and the answer is no".
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
