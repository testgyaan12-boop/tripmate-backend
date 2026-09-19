package com.tripmate.member.entity;

import com.tripmate.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@Entity
@SQLRestriction("is_deleted = 0 AND is_active = 1")
@Table(name = "trip_members",
        uniqueConstraints = @UniqueConstraint(name = "uq_member_trip_user", columnNames = {"trip_id", "user_id"}),
        indexes = @Index(name = "idx_members_trip", columnList = "trip_id"))
public class TripMember extends BaseEntity {

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "role", nullable = false, length = 10)
    private String role = "MEMBER";

    @Column(name = "rsvp", length = 12)
    private String rsvp = "GOING";
}
