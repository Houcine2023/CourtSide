package com.courtside.api.entities;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/** A bookable court inside a club. */
@Entity
@Table(name = "courts")
@Getter
@Setter
public class Court {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(nullable = false, length = 80)
    private String name;

    /**
     * EnumType.STRING, never ORDINAL. ORDINAL stores the enum's position (0,1,2),
     * so reordering the enum silently rewrites the meaning of existing rows.
     * STRING also matches the CHECK constraint in the schema.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Sport sport = Sport.PADEL;

    /** Length of one bookable slot. The DB enforces 30..240 via a CHECK constraint. */
    @Column(name = "slot_minutes", nullable = false)
    private Integer slotMinutes = 90;

    /**
     * BigDecimal for money — never double/float. Binary floating point cannot
     * represent 0.10 exactly, so sums drift by cents. NUMERIC(8,2) <-> BigDecimal.
     */
    @Column(name = "price_per_slot", nullable = false, precision = 8, scale = 2)
    private BigDecimal pricePerSlot;

    /**
     * Soft-delete flag. Courts are deactivated, never deleted: the schema cascades
     * deletes to bookings, so removing a court would erase its booking history.
     */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Optimistic locking. Hibernate adds "WHERE version = ?" to every UPDATE and
     * increments it. If two managers edit the same court concurrently, the second
     * write finds 0 rows updated and throws — nobody's change is silently lost.
     */
    @Version
    @Column(nullable = false)
    private Long version;
}
