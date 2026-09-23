package com.tripmate.billing.entity;

import com.tripmate.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@Entity
@SQLRestriction("is_deleted = 0 AND is_active = 1")
@Table(name = "plan_feature", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"plan_id", "feature_code"})
})
public class PlanFeature extends BaseEntity {

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "feature_code", nullable = false, length = 40)
    private String featureCode;

    @Column(name = "limit_value", nullable = false, length = 40)
    private String limitValue = "";
}
