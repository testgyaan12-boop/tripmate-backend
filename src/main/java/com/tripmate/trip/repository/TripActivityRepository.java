package com.tripmate.trip.repository;

import com.tripmate.trip.entity.TripActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TripActivityRepository extends JpaRepository<TripActivity, Long> {
    List<TripActivity> findByTripIdOrderByCreatedAtDesc(Long tripId);
}
