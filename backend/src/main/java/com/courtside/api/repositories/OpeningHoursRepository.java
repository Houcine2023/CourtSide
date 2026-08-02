package com.courtside.api.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.courtside.api.entities.OpeningHours;

public interface OpeningHoursRepository extends JpaRepository<OpeningHours, Long> {

    List<OpeningHours> findByClubIdOrderByDayOfWeek(Long clubId);

    Optional<OpeningHours> findByClubIdAndDayOfWeek(Long clubId, Integer dayOfWeek);
}
