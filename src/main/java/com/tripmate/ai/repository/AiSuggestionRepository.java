package com.tripmate.ai.repository;

import com.tripmate.ai.entity.AiSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AiSuggestionRepository extends JpaRepository<AiSuggestion, Long> {
    long countByTripIdAndStatusIn(Long tripId, Collection<String> statuses);
    List<AiSuggestion> findByTripIdOrderByCreatedAtDesc(Long tripId);
    Optional<AiSuggestion> findByIdAndTripId(Long id, Long tripId);
}
