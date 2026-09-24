package com.tripmate.billing;

import com.tripmate.billing.entity.PaymentTransaction;
import com.tripmate.billing.entity.Subscription;
import com.tripmate.billing.gateway.PaymentGateway;
import com.tripmate.billing.repository.PaymentTransactionRepository;
import com.tripmate.common.exception.BadRequestException;
import com.tripmate.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * One-time Orders per billing cycle. Verify-on-return (checkout success)
 * is the primary activation path; the webhook is an idempotent backup so
 * activation survives even if the webhook never arrives.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentGateway gateway;
    private final SubscriptionService subs;
    private final PaymentTransactionRepository txns;

    public Map<String, Object> createOrder(Long userId, String planCode, String billing) {
        Subscription plan = subs.planOrThrow(planCode);
        if ("FREE".equals(plan.getCode())) {
            throw new BadRequestException("Free plan needs no payment");
        }
        String cycle = "YEARLY".equalsIgnoreCase(billing) ? "YEARLY" : "MONTHLY";
        long amount = "YEARLY".equals(cycle)
                ? plan.getYearlyPricePaise() : plan.getMonthlyPricePaise();
        if (amount <= 0) throw new BadRequestException("Plan price not configured");
        String receipt = "tm_" + userId + "_" + plan.getCode().toLowerCase()
                + "_" + cycle.toLowerCase() + "_" + System.currentTimeMillis();
        Map<String, Object> order = gateway.createOrder(
                amount, plan.getCurrency() != null ? plan.getCurrency() : "INR", receipt);
        PaymentTransaction t = new PaymentTransaction();
        t.setUserId(userId);
        t.setPaymentGateway(gateway.name());
        t.setGatewayOrderId((String) order.get("orderId"));
        t.setAmountPaise(((Number) order.get("amount")).intValue());
        t.setCurrency((String) order.get("currency"));
        t.setPlanCode(plan.getCode());
        t.setBillingCycle(cycle);
        t.setStatus("CREATED");
        txns.save(t);
        order.put("planCode", plan.getCode());
        order.put("billing", cycle);
        return order;
    }

    @Transactional
    public Map<String, Object> verifyAndActivate(Long userId, String orderId,
                                                 String paymentId, String signature) {
        PaymentTransaction t = txns.findByGatewayOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (!t.getUserId().equals(userId)) {
            throw new BadRequestException("Order belongs to another user");
        }
        if ("CAPTURED".equals(t.getStatus())) {
            return subs.subscriptionDto(userId);
        }
        if (!gateway.verifySignature(orderId, paymentId, signature)) {
            t.setStatus("FAILED");
            txns.save(t);
            throw new BadRequestException("Payment verification failed");
        }
        Map<String, Object> p = gateway.fetchPayment(paymentId);
        if (!"captured".equalsIgnoreCase((String) p.get("status"))) {
            t.setStatus("FAILED");
            txns.save(t);
            throw new BadRequestException("Payment not captured ("
                    + p.get("status") + ")");
        }
        if (((Number) p.get("amount")).longValue() != t.getAmountPaise()) {
            t.setStatus("FAILED");
            txns.save(t);
            throw new BadRequestException("Amount mismatch — possible tampering");
        }
        t.setGatewayPaymentId(paymentId);
        t.setStatus("CAPTURED");
        txns.save(t);
        Map<String, Object> sub = subs.activate(
                userId, t.getPlanCode(), t.getBillingCycle(), t.getPaymentGateway(), t.getId());
        t.setSubscriptionId(subscriptionIdOf(sub));
        txns.save(t);
        return sub;
    }

    /**
     * Webhook path (payment.captured). Idempotent: unknown orders and
     * already-captured payments are ignored so retries are harmless.
     */
    @Transactional
    public Map<String, Object> activateFromWebhook(String orderId, String paymentId, long amountPaise) {
        Optional<PaymentTransaction> ot = txns.findByGatewayOrderId(orderId);
        if (ot.isEmpty()) return Map.of("ok", true, "ignored", true);
        PaymentTransaction t = ot.get();
        if ("CAPTURED".equals(t.getStatus())) {
            return Map.of("ok", true, "ignored", true, "reason", "already-captured");
        }
        if (t.getAmountPaise() != amountPaise) {
            t.setStatus("FAILED");
            txns.save(t);
            return Map.of("ok", true, "ignored", true, "reason", "amount-mismatch");
        }
        t.setGatewayPaymentId(paymentId);
        t.setStatus("CAPTURED");
        txns.save(t);
        Map<String, Object> sub = subs.activate(
                t.getUserId(), t.getPlanCode(), t.getBillingCycle(), t.getPaymentGateway(), t.getId());
        t.setSubscriptionId(subscriptionIdOf(sub));
        txns.save(t);
        return sub;
    }

    @SuppressWarnings("unchecked")
    private static Long subscriptionIdOf(Map<String, Object> sub) {
        Object v = sub.get("subscriptionId");
        return v instanceof Number n ? n.longValue() : null;
    }
}
