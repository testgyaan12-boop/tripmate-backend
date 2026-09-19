package com.tripmate.member.repository;

import com.tripmate.member.entity.TripMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TripMemberRepository extends JpaRepository<TripMember, Long> {
    List<TripMember> findByTripId(Long tripId);
    List<TripMember> findByTripIdIn(Collection<Long> tripIds);
    List<TripMember> findByUserId(Long userId);
    Optional<TripMember> findByTripIdAndUserId(Long tripId, Long userId);
    boolean existsByTripIdAndUserId(Long tripId, Long userId);
}
