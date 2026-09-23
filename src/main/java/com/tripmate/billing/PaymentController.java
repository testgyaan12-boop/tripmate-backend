package com.tripmate.billing;

import com.tripmate.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService payments;
    private final SubscriptionService subs;

    private Long me() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @GetMapping("/plans")
    public ApiResponse<List<Map<String, Object>>> plans() {
        return ApiResponse.ok(subs.plansDto());
    }

    @GetMapping("/subscription/me")
    public ApiResponse<Map<String, Object>> mine() {
        return ApiResponse.ok(subs.subscriptionDto(me()));
    }

    @PostMapping("/orders")
    public ApiResponse<Map<String, Object>> order(@RequestBody Map<String, String> body) {
        return ApiResponse.ok("Order created",
                payments.createOrder(me(), body.get("planCode"), body.get("billing")));
    }

    @PostMapping("/verify")
    public ApiResponse<Map<String, Object>> verify(@RequestBody Map<String, String> body) {
        return ApiResponse.ok("Subscription active", payments.verifyAndActivate(
                me(), body.get("orderId"), body.get("paymentId"), body.get("signature")));
    }
}
