package com.tripmate.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripmate.billing.entity.Subscription;
import com.tripmate.billing.entity.UserSubscription;
import com.tripmate.billing.repository.SubscriptionRepository;
import com.tripmate.billing.repository.UserSubscriptionRepository;
import com.tripmate.common.exception.BadRequestException;
import com.tripmate.config.service.ConfigService;
import com.tripmate.gallery.repository.GalleryItemRepository;
import com.tripmate.member.entity.TripMember;
import com.tripmate.member.repository.TripMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Single-table plans. Pricing, description and feature limits all live in
 * the subscription.details JSON — price/limit changes = SQL UPDATE, no
 * deploy. Missing/disabled rows fall back to safe FREE defaults.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final Map<String, String> FREE_DEFAULTS = Map.of(
            "TRIP_LIMIT", "3",
            "MEMBERS_PER_TRIP", "10",
            "STORAGE_MB", "500",
            "AI_PLANNER", "0",
            "OFFLINE_MAP", "0",
            "EXPORT_PDF", "0",
            "NO_ADS", "0");

    private final SubscriptionRepository plans;
    private final UserSubscriptionRepository subs;
    private final GalleryItemRepository items;
    private final TripMemberRepository tripMembers;
    private final ConfigService config;
    private final ObjectMapper objectMapper;

    /** features map out of details JSON; empty on bad JSON (never throws). */
    @SuppressWarnings("unchecked")
    private Map<String, String> featuresOf(Subscription plan) {
        try {
            if (plan.getDetails() == null) return Map.of();
            Map<String, Object> root = objectMapper.readValue(plan.getDetails(), Map.class);
            Object f = root.get("features");
            if (f instanceof Map<?, ?> fm) {
                Map<String, String> out = new LinkedHashMap<>();
                fm.forEach((k, v) -> out.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
                return out;
            }
        } catch (Exception ignored) {
        }
        return Map.of();
    }

    private String descriptionOf(Subscription plan) {
        try {
            if (plan.getDetails() == null) return "";
            Map<String, Object> root = objectMapper.readValue(plan.getDetails(), Map.class);
            Object d = root.get("description");
            return d == null ? "" : String.valueOf(d);
        } catch (Exception ignored) {
            return "";
        }
    }

    public List<Map<String, Object>> plansDto() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Subscription p : plans.findAllByOrderBySortOrderAsc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", p.getCode());
            m.put("name", p.getName());
            m.put("monthlyPaise", p.getMonthlyPricePaise());
            m.put("yearlyPaise", p.getYearlyPricePaise());
            m.put("currency", p.getCurrency());
            m.put("description", descriptionOf(p));
            m.put("features", featuresOf(p));
            out.add(m);
        }
        return out;
    }

    public Subscription planOrThrow(String planCode) {
        if (planCode == null || planCode.isBlank()) throw new BadRequestException("Plan required");
        return plans.findByCode(planCode.trim().toUpperCase())
                .orElseThrow(() -> new BadRequestException("Plan not available"));
    }

    /** Latest unexpired ACTIVE subscription's plan, else FREE. */
    public Subscription activePlan(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        Optional<UserSubscription> s = subs.findFirstByUserIdAndStatusOrderByEndDateDesc(userId, "ACTIVE");
        if (s.isPresent()) {
            UserSubscription sub = s.get();
            if (sub.getEndDate() == null || sub.getEndDate().isAfter(now)) {
                Optional<Subscription> p = plans.findById(sub.getSubscriptionId());
                if (p.isPresent()) return p.get();
            }
        }
        return freePlan();
    }

    private Subscription freePlan() {
        return plans.findByCode("FREE").orElseGet(() -> {
            Subscription p = new Subscription();
            p.setCode("FREE");
            p.setName("TripMate Free");
            p.setCurrency("INR");
            return p;
        });
    }

    public String limitFor(Long userId, String featureCode) {
        Subscription plan = activePlan(userId);
        String v = featuresOf(plan).get(featureCode);
        if (v != null) return v;
        return FREE_DEFAULTS.getOrDefault(featureCode, "");
    }

    public int intLimit(Long userId, String featureCode, int fallback) {
        try {
            return Integer.parseInt(limitFor(userId, featureCode).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public boolean hasFeature(Long userId, String featureCode) {
        return "1".equals(limitFor(userId, featureCode).trim());
    }

    public boolean isPro(Long userId) {
        return !"FREE".equals(activePlan(userId).getCode());
    }

    public long storageUsedBytes(Long userId) {
        return items.storageUsedByUser(userId);
    }

    public Map<String, Object> subscriptionDto(Long userId) {
        Subscription plan = activePlan(userId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("planCode", plan.getCode());
        m.put("planName", plan.getName());
        LocalDateTime now = LocalDateTime.now();
        Optional<UserSubscription> s = subs.findFirstByUserIdAndStatusOrderByEndDateDesc(userId, "ACTIVE");
        if (s.isPresent() && (s.get().getEndDate() == null || s.get().getEndDate().isAfter(now))) {
            m.put("subscriptionId", s.get().getId());
            m.put("status", "ACTIVE");
            m.put("billing", s.get().getBillingCycle());
            m.put("startDate", s.get().getStartDate());
            m.put("endDate", s.get().getEndDate());
        } else {
            m.put("status", "FREE");
            m.put("billing", null);
            m.put("startDate", null);
            m.put("endDate", null);
        }
        Map<String, String> feats = new LinkedHashMap<>(featuresOf(plan));
        for (Map.Entry<String, String> e : FREE_DEFAULTS.entrySet()) {
            feats.putIfAbsent(e.getKey(), e.getValue());
        }
        m.put("limits", feats);
        m.put("storageUsedBytes", storageUsedBytes(userId));
        return m;
    }

    /**
     * Creates or extends the user's ACTIVE subscription for the plan.
     * Extends from the existing end date when unexpired (stacks cycles).
     */
    @Transactional
    public Map<String, Object> activate(Long userId, String planCode, String billing,
                                        String gateway, Long txnId) {
        Subscription plan = planOrThrow(planCode);
        String cycle = "YEARLY".equalsIgnoreCase(billing) ? "YEARLY" : "MONTHLY";
        LocalDateTime now = LocalDateTime.now();
        Optional<UserSubscription> existing = subs
                .findFirstByUserIdAndSubscriptionIdAndStatusOrderByEndDateDesc(
                        userId, plan.getId(), "ACTIVE");
        UserSubscription s = existing
                .filter(e -> e.getEndDate() == null || e.getEndDate().isAfter(now))
                .orElseGet(UserSubscription::new);
        boolean fresh = s.getId() == null;
        if (fresh) {
            s.setUserId(userId);
            s.setSubscriptionId(plan.getId());
            s.setPaymentGateway(gateway);
            s.setBillingCycle(cycle);
            s.setStartDate(now);
        }
        LocalDateTime base = (!fresh && s.getEndDate() != null && s.getEndDate().isAfter(now))
                ? s.getEndDate() : now;
        s.setEndDate("YEARLY".equals(cycle) ? base.plusYears(1) : base.plusMonths(1));
        s.setStatus("ACTIVE");
        subs.save(s);
        return subscriptionDto(userId);
    }

    /**
     * Enforces MEMBERS_PER_TRIP against the trip OWNER's plan (owner pays
     * for the trip). Falls back to the joining user's plan when no owner row.
     */
    public void checkMemberLimit(Long tripId, Long actorUserId) {
        List<TripMember> all = tripMembers.findByTripId(tripId);
        Long ownerId = all.stream()
                .filter(m -> "OWNER".equals(m.getRole()))
                .map(TripMember::getUserId)
                .findFirst().orElse(actorUserId);
        int limit = intLimit(ownerId, "MEMBERS_PER_TRIP",
                config.getInt("trip.max.members", 20));
        if (all.size() >= limit) {
            throw new BadRequestException("Member limit reached (" + all.size()
                    + "/" + limit + "). Upgrade to Pro for unlimited members.");
        }
    }

    /**
     * Caps how many trips a user can JOIN (role MEMBER only — owned/created
     * trips don't count; creation is capped separately). Pro = unlimited.
     * Limit lives in app_config (free.trip.join.limit); <= 0 disables.
     */
    public void checkJoinLimit(Long userId) {
        if (isPro(userId)) return;
        int limit = config.getInt("free.trip.join.limit", 2);
        if (limit <= 0) return;
        long joined = tripMembers.findByUserId(userId).stream()
                .filter(m -> "MEMBER".equals(m.getRole()))
                .count();
        if (joined >= limit) {
            throw new BadRequestException("You can join max " + limit
                    + " trips (" + joined + " joined). Upgrade to Pro for unlimited.");
        }
    }
}
