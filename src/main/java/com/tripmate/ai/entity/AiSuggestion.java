package com.tripmate.ai.entity;

import com.tripmate.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@Entity
@SQLRestriction("is_deleted = 0 AND is_active = 1")
@Table(name = "ai_place_suggestions",
        indexes = @Index(name = "idx_aisug_trip", columnList = "trip_id"))
public class AiSuggestion extends BaseEntity {

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "provider", length = 20)
    private String provider;

    @Column(name = "model", length = 60)
    private String model;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "status", length = 12)
    private String status = "SUGGESTED";
}
