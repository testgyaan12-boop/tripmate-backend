package com.tripmate.trip.repository;

import com.tripmate.trip.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {
    Optional<Trip> findByInviteCode(String inviteCode);
    List<Trip> findByCreatedByUserId(Long userId);
    long countByCreatedByUserId(Long userId);
}
