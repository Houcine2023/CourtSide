package com.courtside.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.courtside.api.entities.ClubPhoto;

/**
 * Nothing but the JpaRepository contract: the key IS the club id, so every
 * lookup is a primary-key read and there is no query worth declaring here.
 */
public interface ClubPhotoRepository extends JpaRepository<ClubPhoto, Long> {
}
