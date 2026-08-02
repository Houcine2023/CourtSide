package com.courtside.api.entities;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

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

/**
 * A reservation of one court for one time range.
 *
 * The rule that no two active bookings may overlap on the same court is NOT enforced
 * here — it is enforced by the database, through the `no_overlapping_bookings`
 * exclusion constraint created in the Flyway migration. Java checks availability to
 * give users a friendly answer; the constraint is what makes it impossible to cheat,
 * even when two requests arrive in the same millisecond.
 */
@Entity
@Table(name = "bookings")
@Getter
@Setter
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Instants, not wall-clock times: TIMESTAMPTZ stores an absolute point in time, so
     * a booking means the same moment regardless of the reader's time zone.
     */
    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private OffsetDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.CONFIRMED;

    /**
     * The price is frozen at booking time. If the club raises its prices tomorrow,
     * bookings already made keep the price the customer agreed to — a copy here is
     * correct denormalisation, not duplication.
     */
    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal price;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
