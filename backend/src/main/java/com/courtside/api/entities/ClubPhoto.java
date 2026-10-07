package com.courtside.api.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One photo per club, stored beside the club rather than inside it.
 *
 * The primary key IS the club id: there is never a second photo for a club, so a
 * surrogate key plus a unique constraint would add a join for nothing. Keeping
 * the bytes out of {@code clubs} is what lets the club list stay cheap — see the
 * comment at the top of V5__club_photos.sql.
 *
 * There is deliberately no association back to Club. Callers already know which
 * club they are talking about (it came from the URL), and an unmapped foreign
 * key keeps this entity out of every club query entirely.
 */
@Entity
@Table(name = "club_photos")
@Getter
@Setter
public class ClubPhoto {

    @Id
    @Column(name = "club_id")
    private Long clubId;

    /**
     * What we tell the browser the bytes are, echoed straight back on GET. It is
     * validated against a whitelist on upload and the response is served with
     * nosniff plus a sandboxing CSP, so a caller who lies about it still cannot
     * get the bytes interpreted as anything executable.
     */
    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(nullable = false)
    private byte[] data;
}
