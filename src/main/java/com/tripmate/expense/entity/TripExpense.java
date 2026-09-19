package com.tripmate.expense.entity;

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
@Table(name = "trip_expense",
        indexes = @Index(name = "idx_trip_expense_trip", columnList = "trip_id"))
public class TripExpense extends BaseEntity {

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "amount", nullable = false)
    private Double amount = 0.0;

    @Column(name = "category", nullable = false, length = 30)
    private String category;

    @Column(name = "paid_by_user_id", nullable = false)
    private Long paidByUserId;

    @Column(name = "expense_date")
    private LocalDate expenseDate;

    @Column(name = "day_no", nullable = false)
    private Integer dayNo = 1;

    @Column(name = "distance_km")
    private Double distanceKm;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "added_by", nullable = false)
    private Long addedBy;

    @Column(name = "split_type", nullable = false, length = 20)
    private String splitType = "EQUAL";
}
