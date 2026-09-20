package com.tripmate.trip.entity;

import com.tripmate.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "trip_activity", indexes = {
    @Index(name = "idx_activity_trip", columnList = "trip_id"),
    @Index(name = "idx_activity_user", columnList = "user_id")
})
@Getter
@Setter
public class TripActivity extends BaseEntity {

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "action", nullable = false, length = 40)
    private String action;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;
}
