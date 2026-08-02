package com.courtside.api.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create/update payload for a club.
 *
 * managerId is honoured ONLY for ADMIN callers — a manager must not be able to
 * hand their club to someone else (or grab another one). The rule lives in the
 * service, because a DTO cannot know who is calling.
 */
public record ClubRequest(
    @NotBlank @Size(max = 120)
    String name,

    @NotBlank @Size(max = 80)
    String city,

    @NotBlank @Size(max = 255)
    String address,

    Long managerId
) {
}
