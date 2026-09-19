package com.tripmate.place.repository;

import com.tripmate.place.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PlaceRepository extends JpaRepository<Place, Long> {
    List<Place> findByTripId(Long tripId);
    List<Place> findByTripIdIn(Collection<Long> tripIds);
}
