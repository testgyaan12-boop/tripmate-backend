package com.tripmate.trip.repository;

import com.tripmate.trip.entity.TripInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TripInviteRepository extends JpaRepository<TripInvite, Long> {

    List<TripInvite> findByInviteeUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);

    Optional<TripInvite> findByTripIdAndInviteeUserIdAndStatus(Long tripId, Long inviteeUserId, String status);

    boolean existsByTripIdAndInviteeUserIdAndStatus(Long tripId, Long inviteeUserId, String status);

    /** Users who were ever in the same trips as the given user (connections). */
    @Query(value = """
        SELECT DISTINCT tm2.user_id
        FROM trip_members tm1
        JOIN trip_members tm2 ON tm1.trip_id = tm2.trip_id AND tm2.is_deleted = 0
        WHERE tm1.user_id = :userId
          AND tm1.is_deleted = 0
          AND tm2.user_id != :userId
        """, nativeQuery = true)
    List<Long> findConnectionUserIds(@Param("userId") Long userId);
}
