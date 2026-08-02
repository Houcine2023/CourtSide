package com.courtside.api.entities;

import java.time.LocalTime;

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
 * When a club is open, one row per weekday.
 * These are WALL-CLOCK times (LocalTime, no zone): "we open at 08:00" means 08:00
 * local time whatever the date — that is why the column is TIME, not TIMESTAMPTZ.
 */
@Entity
@Table(name = "opening_hours")
@Getter
@Setter
public class OpeningHours {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    /** ISO-8601 numbering: 1 = Monday ... 7 = Sunday (matches java.time.DayOfWeek.getValue()). */
    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(nullable = false)
    private LocalTime opens;

    @Column(nullable = false)
    private LocalTime closes;
}
