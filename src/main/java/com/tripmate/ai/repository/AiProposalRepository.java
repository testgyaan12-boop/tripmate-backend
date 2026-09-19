package com.tripmate.ai.repository;

import com.tripmate.ai.entity.AiProposal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AiProposalRepository extends JpaRepository<AiProposal, Long> {
    long countByTripIdAndStatusIn(Long tripId, Collection<String> statuses);
    List<AiProposal> findByTripIdOrderByCreatedAtDesc(Long tripId);
    Optional<AiProposal> findByIdAndTripId(Long id, Long tripId);
    Optional<AiProposal> findFirstByTripIdAndStatusOrderByCreatedAtDesc(Long tripId, String status);
}
