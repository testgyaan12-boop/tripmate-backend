package com.tripmate.expense.service;

import com.tripmate.common.exception.BadRequestException;
import com.tripmate.expense.entity.TripBudget;
import com.tripmate.expense.repository.TripBudgetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final TripBudgetRepository budgets;
    private final com.tripmate.expense.repository.TripExpenseRepository expenses;

    public Map<String, Object> getBudget(Long tripId) {
        List<TripBudget> plans = budgets.findByTripId(tripId);
        List<Map<String, Object>> items = new ArrayList<>();
        double totalPlanned = 0;
        double totalSpent = 0;
        for (TripBudget b : plans) {
            double planned = b.getPlannedAmount() == null ? 0 : b.getPlannedAmount();
            double spent = b.getSpentAmount() == null ? 0 : b.getSpentAmount();
            totalPlanned += planned;
            totalSpent += spent;
            items.add(Map.of(
                    "category", b.getCategory(),
                    "planned", round(planned),
                    "spent", round(spent),
                    "progress", planned > 0 ? Math.min(1.0, spent / planned) : 0.0));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("totalPlanned", round(totalPlanned));
        out.put("totalSpent", round(totalSpent));
        out.put("progress",
                totalPlanned > 0 ? Math.min(1.0, totalSpent / totalPlanned) : 0.0);
        return out;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> saveBudget(Long tripId, Map<String, Object> body) {
        Object raw = body.get("items");
        List<Map<String, Object>> items = new ArrayList<>();
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (o instanceof Map) items.add((Map<String, Object>) o);
            }
        } else if (body.get("category") != null) {
            items.add(body);
        }
        if (items.isEmpty()) throw new BadRequestException("No budget items provided");
        for (Map<String, Object> it : items) {
            String category = it.get("category") == null ? null : it.get("category").toString();
            if (category == null || category.isBlank()) continue;
            double planned = it.get("planned") instanceof Number
                    ? ((Number) it.get("planned")).doubleValue() : 0;
            if (planned < 0) throw new BadRequestException("Planned amount cannot be negative");
            TripBudget b = budgets.findByTripIdAndCategory(tripId, category)
                    .orElseGet(() -> {
                        TripBudget nb = new TripBudget();
                        nb.setTripId(tripId);
                        nb.setCategory(category);
                        return nb;
                    });
            b.setPlannedAmount(planned);
            budgets.save(b);
        }
        backfillSpent(tripId);
        return getBudget(tripId);
    }

    /** Recomputes spent from existing expenses (covers budgets created late). */
    private void backfillSpent(Long tripId) {
        Map<String, Double> spent = new java.util.HashMap<>();
        for (com.tripmate.expense.entity.TripExpense e : expenses.findByTripId(tripId)) {
            spent.merge(e.getCategory(), e.getAmount() == null ? 0 : e.getAmount(), Double::sum);
        }
        for (TripBudget b : budgets.findByTripId(tripId)) {
            b.setSpentAmount(spent.getOrDefault(b.getCategory(), 0.0));
            budgets.save(b);
        }
    }

    /** Adds delta to the stored spent_amount (only when a budget row exists). */
    public void trackSpending(Long tripId, String category, double delta) {
        budgets.findByTripIdAndCategory(tripId, category).ifPresent(b -> {
            double spent = b.getSpentAmount() == null ? 0 : b.getSpentAmount();
            b.setSpentAmount(Math.max(0, spent + delta));
            budgets.save(b);
        });
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
