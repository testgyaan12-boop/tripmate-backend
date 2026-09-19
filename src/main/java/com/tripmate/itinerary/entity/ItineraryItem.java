package com.tripmate.itinerary.entity;

import com.tripmate.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@Entity
@SQLRestriction("is_deleted = 0 AND is_active = 1")
@Table(name = "itinerary_items", indexes = @Index(name = "idx_itin_trip", columnList = "trip_id,day_no"))
public class ItineraryItem extends BaseEntity {

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "day_no", nullable = false)
    private Integer dayNo;

    @Column(name = "place_id")
    private Long placeId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "distance_km")
    private Double distanceKm;

    @Column(name = "drive_mins")
    private Integer driveMins;

    @Column(name = "stay_notes")
    private String stayNotes;

    @Column(name = "stops_json", columnDefinition = "TEXT")
    private String stopsJson;

    @Column(name = "food_notes", columnDefinition = "TEXT")
    private String foodNotes;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;
}
