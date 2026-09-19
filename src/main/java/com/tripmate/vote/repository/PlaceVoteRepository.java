package com.tripmate.vote.repository;

import com.tripmate.vote.entity.PlaceVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PlaceVoteRepository extends JpaRepository<PlaceVote, Long> {
    Optional<PlaceVote> findByPlaceIdAndUserId(Long placeId, Long userId);
    List<PlaceVote> findByPlaceId(Long placeId);
    long countByUserId(Long userId);

    @Query("SELECT v.voteStatus, COUNT(v) FROM PlaceVote v WHERE v.placeId = :placeId GROUP BY v.voteStatus")
    List<Object[]> countByPlaceIdGrouped(Long placeId);
}
