package com.tripmate.billing.entity;

import com.tripmate.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

/**
 * Single subscription/plan table. Pricing, description and feature limits
 * all live here — features inside the details JSON:
 * {"description":"...","features":{"TRIP_LIMIT":"3",...}}.
 * Price/limit changes = SQL UPDATE, no deploy.
 */
@Getter
@Setter
@Entity
@SQLRestriction("is_deleted = 0 AND is_active = 1")
@Table(name = "subscription")
public class Subscription extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "monthly_price_paise", nullable = false)
    private Integer monthlyPricePaise = 0;

    @Column(name = "yearly_price_paise", nullable = false)
    private Integer yearlyPricePaise = 0;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "INR";

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "details", nullable = false, columnDefinition = "jsonb")
    private String details = "{\"features\":{}}";
}
