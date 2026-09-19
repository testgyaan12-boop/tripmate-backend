package com.tripmate.expense.entity;

import com.tripmate.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@Entity
@SQLRestriction("is_deleted = 0 AND is_active = 1")
@Table(name = "trip_budget",
        uniqueConstraints = @UniqueConstraint(name = "uq_trip_budget_cat",
                columnNames = {"trip_id", "category"}),
        indexes = @Index(name = "idx_trip_budget_trip", columnList = "trip_id"))
public class TripBudget extends BaseEntity {

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "category", nullable = false, length = 30)
    private String category;

    @Column(name = "planned_amount", nullable = false)
    private Double plannedAmount = 0.0;

    @Column(name = "spent_amount", nullable = false)
    private Double spentAmount = 0.0;
}
