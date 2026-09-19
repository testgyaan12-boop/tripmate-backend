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
@Table(name = "expense_split",
        indexes = @Index(name = "idx_expense_split_expense", columnList = "expense_id"))
public class ExpenseSplit extends BaseEntity {

    @Column(name = "expense_id", nullable = false)
    private Long expenseId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "amount", nullable = false)
    private Double amount = 0.0;

    @Column(name = "status", nullable = false, length = 10)
    private String status = "PENDING";

    @Column(name = "settled_via", length = 20)
    private String settledVia;
}
