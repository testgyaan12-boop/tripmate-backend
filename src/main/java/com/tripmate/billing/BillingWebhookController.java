package com.tripmate.billing;

import com.tripmate.config.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Razorpay webhook (payment.captured). Public by design — authenticity comes
 * from the HMAC-SHA256 signature, not JWT. Idempotent: retries are harmless.
 * Configure in Razorpay dashboard -> Settings -> Webhooks with the secret
 * stored in app_config RAZORPAY_WEBHOOK_SECRET.
 */
@RestController
@RequestMapping("/api/billing/webhook")
@RequiredArgsConstructor
public class BillingWebhookController {

    private final PaymentService payments;
    private final ConfigService config;

    @PostMapping("/razorpay")
    public ResponseEntity<Map<String, Object>> razorpay(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        String secret = config.get("RAZORPAY_WEBHOOK_SECRET");
        if (secret.isBlank() || secret.startsWith("REPLACE") || !hmacOk(rawBody, signature, secret)) {
            return ResponseEntity.status(400).body(Map.of("ok", false, "error", "bad signature"));
        }
        try {
            JSONObject evt = new JSONObject(rawBody);
            if (!"payment.captured".equals(evt.optString("event", ""))) {
                return ResponseEntity.ok(Map.of("ok", true, "ignored", true));
            }
            JSONObject entity = evt.getJSONObject("payload")
                    .getJSONObject("payment").getJSONObject("entity");
            String paymentId = entity.getString("id");
            String orderId = entity.optString("order_id", null);
            long amount = entity.optLong("amount", -1);
            if (orderId == null || orderId.isBlank() || amount < 0) {
                return ResponseEntity.ok(Map.of("ok", true, "ignored", true));
            }
            return ResponseEntity.ok(payments.activateFromWebhook(orderId, paymentId, amount));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("ok", false, "error", "bad payload"));
        }
    }

    static boolean hmacOk(String body, String signature, String secret) {
        if (body == null || signature == null || signature.isBlank()) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return constantTimeEquals(hex.toString(), signature.trim());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }
}
