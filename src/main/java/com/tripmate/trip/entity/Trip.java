package com.tripmate.trip.entity;

import com.tripmate.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@SQLRestriction("is_deleted = 0 AND is_active = 1")
@Table(name = "trips", indexes = {
        @Index(name = "idx_trips_invite", columnList = "invite_code"),
        @Index(name = "idx_trips_alive", columnList = "is_deleted,is_active")
})
public class Trip extends BaseEntity {

    @Column(name = "trip_name", nullable = false)
    private String tripName;

    @Column(name = "start_name")
    private String startName;

    @Column(name = "start_lat")
    private Double startLat;

    @Column(name = "start_lng")
    private Double startLng;

    @Column(name = "dest_name")
    private String destName;

    @Column(name = "dest_lat")
    private Double destLat;

    @Column(name = "dest_lng")
    private Double destLng;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "days_count")
    private Integer daysCount;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PLANNING";

    @Column(name = "invite_code", unique = true, length = 12)
    private String inviteCode;

    @Column(name = "route_geojson", columnDefinition = "TEXT")
    private String routeGeojson;

    @Column(name = "total_km")
    private Double totalKm;
}
