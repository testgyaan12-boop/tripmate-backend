package com.tripmate.billing.gateway;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import com.tripmate.common.exception.BadRequestException;
import com.tripmate.config.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component("razorpayGateway")
@RequiredArgsConstructor
public class RazorpayGateway implements PaymentGateway {

    private final ConfigService config;

    @Override
    public String name() {
        return "RAZORPAY";
    }

    private RazorpayClient client() throws RazorpayException {
        String key = config.get("RAZORPAY_KEY_ID");
        String secret = config.get("RAZORPAY_SECRET");
        if (key.isBlank() || secret.isBlank() || secret.startsWith("REPLACE")) {
            throw new BadRequestException("Razorpay not configured");
        }
        return new RazorpayClient(key, secret);
    }

    @Override
    public Map<String, Object> createOrder(long amountPaise, String currency, String receipt) {
        try {
            JSONObject req = new JSONObject();
            req.put("amount", amountPaise);
            req.put("currency", currency);
            req.put("receipt", receipt);
            req.put("payment_capture", 1);
            com.razorpay.Order order = client().orders.create(req);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("orderId", order.get("id").toString());
            out.put("amount", Long.parseLong(order.get("amount").toString()));
            out.put("currency", order.get("currency").toString());
            out.put("keyId", config.get("RAZORPAY_KEY_ID"));
            return out;
        } catch (RazorpayException e) {
            throw new BadRequestException("Razorpay order failed: " + e.getMessage());
        }
    }

    @Override
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        try {
            JSONObject attrs = new JSONObject();
            attrs.put("razorpay_order_id", orderId);
            attrs.put("razorpay_payment_id", paymentId);
            attrs.put("razorpay_signature", signature);
            Utils.verifyPaymentSignature(attrs, config.get("RAZORPAY_SECRET"));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Map<String, Object> fetchPayment(String paymentId) {
        try {
            com.razorpay.Payment p = client().payments.fetch(paymentId);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", p.get("id").toString());
            out.put("status", p.get("status").toString());
            out.put("amount", Long.parseLong(p.get("amount").toString()));
            Object orderId = p.get("order_id");
            out.put("orderId", orderId == null || JSONObject.NULL.equals(orderId)
                    ? null : orderId.toString());
            Object method = p.get("method");
            out.put("method", method == null || JSONObject.NULL.equals(method)
                    ? null : method.toString());
            return out;
        } catch (RazorpayException e) {
            throw new BadRequestException("Razorpay fetch failed: " + e.getMessage());
        }
    }
}
