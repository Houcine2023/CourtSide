package com.courtside.api.entities;

/**
 * Must stay in sync with the CHECK constraint on courts.sport in the Flyway schema.
 * Adding a value here alone is not enough — the database would reject the row.
 */
public enum Sport {
    PADEL,
    TENNIS,
    SQUASH
}
