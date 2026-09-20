package com.tripmate.trip.entity;

import com.tripmate.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "trip_invite", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"trip_id", "invitee_user_id", "is_deleted"})
}, indexes = {
    @Index(name = "idx_invite_invitee", columnList = "invitee_user_id, status"),
    @Index(name = "idx_invite_trip", columnList = "trip_id")
})
@Getter
@Setter
public class TripInvite extends BaseEntity {

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "inviter_user_id", nullable = false)
    private Long inviterUserId;

    @Column(name = "invitee_user_id", nullable = false)
    private Long inviteeUserId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";
}
