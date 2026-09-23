package com.tripmate.billing;

import com.tripmate.billing.entity.PlanFeature;
import com.tripmate.billing.entity.PlanMaster;
import com.tripmate.billing.entity.UserSubscription;
import com.tripmate.billing.repository.PlanFeatureRepository;
import com.tripmate.billing.repository.PlanMasterRepository;
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
 * DB-driven plans. Pricing/limits change via plan_master + plan_feature —
 * no deploy. Missing/disabled plan rows fall back to safe FREE defaults.
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

    private final PlanMasterRepository plans;
    private final PlanFeatureRepository features;
    private final UserSubscriptionRepository subs;
    private final GalleryItemRepository items;
    private final TripMemberRepository tripMembers;
    private final ConfigService config;

    public List<Map<String, Object>> plansDto() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (PlanMaster p : plans.findAllByOrderBySortOrderAsc()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", p.getPlanCode());
            m.put("name", p.getPlanName());
            m.put("monthlyPaise", p.getMonthlyPricePaise());
            m.put("yearlyPaise", p.getYearlyPricePaise());
            m.put("currency", p.getCurrency());
            Map<String, String> feats = new LinkedHashMap<>();
            for (PlanFeature f : features.findByPlanId(p.getId())) {
                feats.put(f.getFeatureCode(), f.getLimitValue());
            }
            m.put("features", feats);
            out.add(m);
        }
        return out;
    }

    public PlanMaster planOrThrow(String planCode) {
        if (planCode == null || planCode.isBlank()) throw new BadRequestException("Plan required");
        return plans.findByPlanCode(planCode.trim().toUpperCase())
                .orElseThrow(() -> new BadRequestException("Plan not available"));
    }

    /** Latest unexpired ACTIVE subscription's plan, else FREE. */
    public PlanMaster activePlan(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        Optional<UserSubscription> s = subs.findFirstByUserIdAndStatusOrderByEndDateDesc(userId, "ACTIVE");
        if (s.isPresent()) {
            UserSubscription sub = s.get();
            if (sub.getEndDate() == null || sub.getEndDate().isAfter(now)) {
                Optional<PlanMaster> p = plans.findById(sub.getPlanId());
                if (p.isPresent()) return p.get();
            }
        }
        return freePlan();
    }

    private PlanMaster freePlan() {
        return plans.findByPlanCode("FREE").orElseGet(() -> {
            PlanMaster p = new PlanMaster();
            p.setPlanCode("FREE");
            p.setPlanName("TripMate Free");
            p.setCurrency("INR");
            return p;
        });
    }

    public String limitFor(Long userId, String featureCode) {
        PlanMaster plan = activePlan(userId);
        if (plan.getId() != null) {
            for (PlanFeature f : features.findByPlanId(plan.getId())) {
                if (featureCode.equals(f.getFeatureCode())) return f.getLimitValue();
            }
        }
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
        return !"FREE".equals(activePlan(userId).getPlanCode());
    }

    public long storageUsedBytes(Long userId) {
        return items.storageUsedByUser(userId);
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

    public Map<String, Object> subscriptionDto(Long userId) {
        PlanMaster plan = activePlan(userId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("planCode", plan.getPlanCode());
        m.put("planName", plan.getPlanName());
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
        Map<String, String> feats = new LinkedHashMap<>();
        if (plan.getId() != null) {
            for (PlanFeature f : features.findByPlanId(plan.getId())) {
                feats.put(f.getFeatureCode(), f.getLimitValue());
            }
        }
        feats.putAll(FREE_DEFAULTS.entrySet().stream()
                .filter(e -> !feats.containsKey(e.getKey()))
                .collect(LinkedHashMap::new,
                        (mm, e) -> mm.put(e.getKey(), e.getValue()),
                        LinkedHashMap::putAll));
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
        PlanMaster plan = planOrThrow(planCode);
        String cycle = "YEARLY".equalsIgnoreCase(billing) ? "YEARLY" : "MONTHLY";
        LocalDateTime now = LocalDateTime.now();
        Optional<UserSubscription> existing = subs
                .findFirstByUserIdAndPlanIdAndStatusOrderByEndDateDesc(userId, plan.getId(), "ACTIVE");
        UserSubscription s = existing
                .filter(e -> e.getEndDate() == null || e.getEndDate().isAfter(now))
                .orElseGet(UserSubscription::new);
        boolean fresh = s.getId() == null;
        if (fresh) {
            s.setUserId(userId);
            s.setPlanId(plan.getId());
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
}
