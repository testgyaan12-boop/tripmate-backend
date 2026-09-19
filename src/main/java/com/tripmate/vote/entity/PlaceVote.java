package com.tripmate.vote.entity;

import com.tripmate.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@Entity
@SQLRestriction("is_deleted = 0 AND is_active = 1")
@Table(name = "place_votes",
        uniqueConstraints = @UniqueConstraint(name = "uq_vote_place_user", columnNames = {"place_id", "user_id"}),
        indexes = @Index(name = "idx_votes_place", columnList = "place_id"))
public class PlaceVote extends BaseEntity {

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "vote_status", nullable = false, length = 10)
    private String voteStatus;
}
