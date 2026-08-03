package com.courtside.api.entities;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * "Tell me if this slot frees up."
 *
 * Ordering is by created_at: the queue is first-come-first-served, which is the only
 * ordering users perceive as fair.
 */
@Entity
@Table(name = "waitlist_entries")
@Getter
@Setter
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private OffsetDateTime endTime;

    /** Set when the user has been told the slot is free. NULL = still waiting. */
    @Column(name = "notified_at")
    private OffsetDateTime notifiedAt;

    /**
     * false once the entry is consumed, cancelled or expired. Kept rather than deleted
     * so we can answer "were you ever notified?" and so the unique index only guards
     * ACTIVE entries — a user may re-join the same slot after leaving.
     */
    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
