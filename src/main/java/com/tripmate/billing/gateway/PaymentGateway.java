package com.tripmate.billing.gateway;

import java.util.Map;

/**
 * Payment provider seam. Razorpay now; Stripe later = new implementation
 * only, no app-logic change. Amounts in smallest currency unit (paise).
 */
public interface PaymentGateway {

    /** Provider code: RAZORPAY, STRIPE. */
    String name();

    /**
     * Creates a one-time order. Returns {orderId, amount, currency, keyId}.
     */
    Map<String, Object> createOrder(long amountPaise, String currency, String receipt);

    /**
     * Verifies the checkout success signature
     * (Razorpay: HMAC-SHA256 over order_id|payment_id).
     */
    boolean verifySignature(String orderId, String paymentId, String signature);

    /**
     * Fetches a payment from the provider.
     * Returns {id, status, amount, orderId, method}.
     */
    Map<String, Object> fetchPayment(String paymentId);
}
