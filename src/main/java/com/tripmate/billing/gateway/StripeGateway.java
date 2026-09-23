package com.tripmate.billing.gateway;

import java.util.Map;

/**
 * International gateway (future). Wire as a Spring bean and select by
 * user region/currency when STRIPE_SECRET_KEY exists in app_config.
 * Not a bean yet on purpose — Razorpay is the single active bean.
 */
public class StripeGateway implements PaymentGateway {

    @Override
    public String name() {
        return "STRIPE";
    }

    @Override
    public Map<String, Object> createOrder(long amountPaise, String currency, String receipt) {
        throw new UnsupportedOperationException("Stripe not wired yet");
    }

    @Override
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        throw new UnsupportedOperationException("Stripe not wired yet");
    }

    @Override
    public Map<String, Object> fetchPayment(String paymentId) {
        throw new UnsupportedOperationException("Stripe not wired yet");
    }
}
