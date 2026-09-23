package com.tripmate.billing.entity;

import com.tripmate.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@SQLRestriction("is_deleted = 0 AND is_active = 1")
@Table(name = "user_subscription", indexes = {
        @Index(name = "idx_user_sub_user", columnList = "user_id,is_deleted,is_active")
})
public class UserSubscription extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "payment_gateway", nullable = false, length = 20)
    private String paymentGateway = "RAZORPAY";

    @Column(name = "gateway_subscription_id", length = 100)
    private String gatewaySubscriptionId;

    @Column(name = "billing_cycle", nullable = false, length = 10)
    private String billingCycle = "MONTHLY";

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";
}
