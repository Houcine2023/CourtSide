package com.courtside.api.dtos;

import com.courtside.api.entities.Club;

/**
 * What the API returns for a club. Note what is NOT here: the manager's email,
 * password hash, or the whole User object. A response DTO is also a privacy filter.
 */
public record ClubResponse(
    Long id,
    String name,
    String city,
    String address,
    Long managerId,
    String managerName,
    String photoUrl
) {
    /**
     * Mapping lives next to the DTO (a static factory) rather than in the service:
     * one place to change when the shape evolves.
     * Careful: manager is LAZY, so only call this inside a transaction or after a
     * fetch join — otherwise Hibernate cannot initialise the proxy.
     *
     * photoUrl is always the canonical address, never null when a photo happens to
     * be missing. Answering that would need a query against club_photos on every
     * listing row, and the browser already knows how to handle an image that is not
     * there: the card falls back to its placeholder tile. One field, no extra
     * round-trip, and the URL never goes stale when a photo is added or removed.
     */
    public static ClubResponse from(Club club) {
        return new ClubResponse(
            club.getId(),
            club.getName(),
            club.getCity(),
            club.getAddress(),
            club.getManager() == null ? null : club.getManager().getId(),
            club.getManager() == null ? null : club.getManager().getFullName(),
            "/api/v1/clubs/" + club.getId() + "/photo"
        );
    }
}
