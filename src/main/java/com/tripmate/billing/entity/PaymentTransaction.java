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
@Table(name = "payment_transaction", indexes = {
        @Index(name = "idx_pay_txn_user", columnList = "user_id,is_deleted,is_active")
})
public class PaymentTransaction extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "subscription_id")
    private Long subscriptionId;

    @Column(name = "payment_gateway", nullable = false, length = 20)
    private String paymentGateway = "RAZORPAY";

    @Column(name = "gateway_order_id", length = 100)
    private String gatewayOrderId;

    @Column(name = "gateway_payment_id", length = 100)
    private String gatewayPaymentId;

    @Column(name = "amount_paise", nullable = false)
    private Integer amountPaise = 0;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "INR";

    @Column(name = "plan_code", length = 20)
    private String planCode;

    @Column(name = "billing_cycle", length = 10)
    private String billingCycle;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "CREATED";
}
