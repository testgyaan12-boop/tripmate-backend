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
@Table(name = "plan_master")
public class PlanMaster extends BaseEntity {

    @Column(name = "plan_code", nullable = false, unique = true, length = 20)
    private String planCode;

    @Column(name = "plan_name", nullable = false, length = 50)
    private String planName;

    @Column(name = "monthly_price_paise", nullable = false)
    private Integer monthlyPricePaise = 0;

    @Column(name = "yearly_price_paise", nullable = false)
    private Integer yearlyPricePaise = 0;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "INR";

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
