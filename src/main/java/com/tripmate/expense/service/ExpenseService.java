package com.tripmate.expense.service;

import com.tripmate.common.exception.BadRequestException;
import com.tripmate.common.exception.ResourceNotFoundException;
import com.tripmate.expense.entity.ExpenseSplit;
import com.tripmate.expense.entity.TripExpense;
import com.tripmate.expense.repository.ExpenseSplitRepository;
import com.tripmate.expense.repository.TripExpenseRepository;
import com.tripmate.member.entity.TripMember;
import com.tripmate.member.repository.TripMemberRepository;
import com.tripmate.trip.entity.Trip;
import com.tripmate.trip.repository.TripRepository;
import com.tripmate.user.entity.User;
import com.tripmate.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final TripExpenseRepository expenses;
    private final ExpenseSplitRepository splits;
    private final TripMemberRepository members;
    private final TripRepository trips;
    private final UserRepository users;
    private final BudgetService budgetService;

    private Set<Long> memberIds(Long tripId) {
        Set<Long> ids = new HashSet<>();
        for (TripMember m : members.findByTripId(tripId)) {
            ids.add(m.getUserId());
        }
        return ids;
    }

    private String nameOf(Long userId) {
        return users.findById(userId)
                .map(User::getName).orElse("User#" + userId);
    }

    // ---------------- create / update / delete ----------------

    public Map<String, Object> create(Long tripId, Long userId, Map<String, Object> body) {
        String title = body.get("title") == null ? "" : body.get("title").toString().trim();
        if (title.isEmpty()) throw new BadRequestException("Title required");
        double amount = body.get("amount") instanceof Number
                ? ((Number) body.get("amount")).doubleValue() : 0;
        if (amount <= 0) throw new BadRequestException("Amount must be positive");
        Long paidBy = body.get("paidBy") instanceof Number
                ? ((Number) body.get("paidBy")).longValue() : userId;

        TripExpense e = new TripExpense();
        e.setTripId(tripId);
        e.setTitle(title);
        e.setAmount(amount);
        e.setCategory(body.get("category") == null ? "OTHER" : body.get("category").toString());
        e.setPaidByUserId(paidBy);
        e.setExpenseDate(parseDate(body.get("expenseDate")));
        e.setDayNo(body.get("dayNo") instanceof Number
                ? ((Number) body.get("dayNo")).intValue() : 1);
        e.setDistanceKm(body.get("distanceKm") instanceof Number
                ? ((Number) body.get("distanceKm")).doubleValue() : null);
        e.setNotes(body.get("notes") == null ? null : body.get("notes").toString());
        e.setAddedBy(userId);
        e.setSplitType(body.get("splitType") == null
                ? "EQUAL" : body.get("splitType").toString());
        expenses.save(e);

        Map<Long, Double> shares =
                computeShares(e.getSplitType(), amount, asList(body.get("splitMembers")), memberIds(tripId));
        saveSplits(e, shares);
        budgetService.trackSpending(tripId, e.getCategory(), amount);
        return enriched(e);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> update(Long tripId, Long expenseId, Map<String, Object> body) {
        TripExpense e = expenses.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found"));
        if (!e.getTripId().equals(tripId)) {
            throw new BadRequestException("Expense does not belong to this trip");
        }
        double oldAmount = e.getAmount() == null ? 0 : e.getAmount();
        String oldCategory = e.getCategory();
        boolean splitsDirty = false;

        if (body.get("title") != null) e.setTitle(body.get("title").toString());
        if (body.get("amount") instanceof Number) {
            e.setAmount(((Number) body.get("amount")).doubleValue());
            splitsDirty = true;
        }
        if (body.get("category") != null) e.setCategory(body.get("category").toString());
        if (body.get("expenseDate") != null || body.containsKey("expenseDate")) {
            e.setExpenseDate(parseDate(body.get("expenseDate")));
        }
        if (body.get("dayNo") instanceof Number) {
            e.setDayNo(((Number) body.get("dayNo")).intValue());
        }
        if (body.containsKey("distanceKm")) {
            e.setDistanceKm(body.get("distanceKm") instanceof Number
                    ? ((Number) body.get("distanceKm")).doubleValue() : null);
        }
        if (body.containsKey("notes")) {
            e.setNotes(body.get("notes") == null ? null : body.get("notes").toString());
        }
        if (body.get("paidBy") instanceof Number) {
            e.setPaidByUserId(((Number) body.get("paidBy")).longValue());
            splitsDirty = true;
        }
        if (body.get("splitType") != null) {
            e.setSplitType(body.get("splitType").toString());
            splitsDirty = true;
        }
        if (body.containsKey("splitMembers")) splitsDirty = true;
        if (e.getAmount() == null || e.getAmount() <= 0) {
            throw new BadRequestException("Amount must be positive");
        }
        expenses.save(e);

        if (splitsDirty) {
            Object raw = body.containsKey("splitMembers")
                    ? body.get("splitMembers") : null;
            Map<Long, Double> shares = computeShares(e.getSplitType(), e.getAmount(),
                    raw == null ? null : asList(raw), memberIds(tripId));
            retireSplits(e.getId());
            saveSplits(e, shares);
        }
        double diff = e.getAmount() - oldAmount;
        if (!oldCategory.equals(e.getCategory())) {
            budgetService.trackSpending(tripId, oldCategory, -oldAmount);
            budgetService.trackSpending(tripId, e.getCategory(), e.getAmount());
        } else if (Math.abs(diff) > 0.0001) {
            budgetService.trackSpending(tripId, e.getCategory(), diff);
        }
        return enriched(e);
    }

    public void delete(Long tripId, Long expenseId) {
        TripExpense e = expenses.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found"));
        if (!e.getTripId().equals(tripId)) {
            throw new BadRequestException("Expense does not belong to this trip");
        }
        for (ExpenseSplit s : splits.findByExpenseId(e.getId())) {
            s.softDelete();
            splits.save(s);
        }
        budgetService.trackSpending(tripId, e.getCategory(), -(e.getAmount() == null ? 0 : e.getAmount()));
        e.softDelete();
        expenses.save(e);
    }

    public Map<String, Object> markSettled(Long tripId, Long expenseId, boolean settled) {
        TripExpense e = expenses.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found"));
        if (!e.getTripId().equals(tripId)) {
            throw new BadRequestException("Expense does not belong to this trip");
        }
        for (ExpenseSplit s : splits.findByExpenseId(e.getId())) {
            if (settled) {
                s.setStatus("PAID");
            } else if (!s.getUserId().equals(e.getPaidByUserId())) {
                s.setStatus("PENDING");
                s.setSettledVia(null);
            }
            splits.save(s);
        }
        return enriched(e);
    }

    // ---------------- list ----------------

    public List<Map<String, Object>> listByTrip(Long tripId) {
        List<TripExpense> all = expenses.findByTripId(tripId);
        all.sort((a, b) -> {
            if (a.getExpenseDate() == null && b.getExpenseDate() == null) {
                return Long.compare(b.getId(), a.getId());
            }
            if (a.getExpenseDate() == null) return 1;
            if (b.getExpenseDate() == null) return -1;
            int c = b.getExpenseDate().compareTo(a.getExpenseDate());
            return c != 0 ? c : Long.compare(b.getId(), a.getId());
        });
        List<Map<String, Object>> out = new ArrayList<>();
        for (TripExpense e : all) out.add(enriched(e));
        return out;
    }

    public Map<String, Object> enriched(TripExpense e) {
        List<ExpenseSplit> rows = splits.findByExpenseId(e.getId());
        List<Map<String, Object>> splitOut = new ArrayList<>();
        boolean allPaid = !rows.isEmpty();
        for (ExpenseSplit s : rows) {
            if (!"PAID".equals(s.getStatus())) allPaid = false;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", s.getUserId());
            m.put("name", nameOf(s.getUserId()));
            m.put("amount", s.getAmount());
            m.put("status", s.getStatus());
            m.put("settledVia", s.getSettledVia());
            splitOut.add(m);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("tripId", e.getTripId());
        m.put("title", e.getTitle());
        m.put("amount", e.getAmount());
        m.put("category", e.getCategory());
        m.put("paidBy", e.getPaidByUserId());
        m.put("paidByName", nameOf(e.getPaidByUserId()));
        m.put("addedBy", e.getAddedBy());
        m.put("addedByName", nameOf(e.getAddedBy()));
        m.put("expenseDate", e.getExpenseDate() == null ? null : e.getExpenseDate().toString());
        m.put("dayNo", e.getDayNo());
        m.put("distanceKm", e.getDistanceKm());
        m.put("notes", e.getNotes());
        m.put("splitType", e.getSplitType());
        m.put("splitCount", rows.size());
        m.put("status", allPaid ? "Settled" : "Pending");
        m.put("splits", splitOut);
        m.put("createdAt", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
        return m;
    }

    // ---------------- settlement ----------------

    /**
     * Spec settlement: pending splits grouped into you-owe / you-receive
     * plus a minimal global settle plan and recorded payment history.
     */
    public Map<String, Object> settlements(Long tripId, Long me) {
        Set<Long> ids = memberIds(tripId);
        List<TripExpense> all = expenses.findByTripId(tripId);
        Map<Long, TripExpense> byId = new HashMap<>();
        List<Long> eids = new ArrayList<>();
        for (TripExpense e : all) {
            byId.put(e.getId(), e);
            eids.add(e.getId());
        }
        Map<Long, ExpenseSplit> splitById = new HashMap<>();
        List<ExpenseSplit> rows = eids.isEmpty()
                ? List.of() : splits.findByExpenseIdIn(eids);
        for (ExpenseSplit s : rows) splitById.put(s.getId(), s);

        double totalSpent = 0;
        Map<Long, Double> paid = new HashMap<>();
        for (TripExpense e : all) {
            double a = e.getAmount() == null ? 0 : e.getAmount();
            totalSpent += a;
            paid.merge(e.getPaidByUserId(), a, Double::sum);
        }

        // Outstanding graph from PENDING splits only: debtor -> creditor -> amount.
        // Recorded cash payments already shrank the pending rows, so the
        // net of this graph is exactly what is still owed.
        Map<Long, Map<Long, Double>> pending = new HashMap<>();
        for (ExpenseSplit s : rows) {
            if (!"PENDING".equals(s.getStatus())) continue;
            TripExpense e = byId.get(s.getExpenseId());
            if (e == null || s.getUserId().equals(e.getPaidByUserId())) continue;
            pending.computeIfAbsent(s.getUserId(), k -> new HashMap<>())
                    .merge(e.getPaidByUserId(), s.getAmount(), Double::sum);
        }

        // Per-pair netting for "you owe / you receive".
        Map<Long, Double> myPair = new HashMap<>();
        for (var en : pending.entrySet()) {
            Long debtor = en.getKey();
            for (var en2 : en.getValue().entrySet()) {
                Long creditor = en2.getKey();
                double v = en2.getValue();
                if (debtor.equals(me) && !creditor.equals(me)) {
                    myPair.merge(creditor, v, Double::sum);
                } else if (creditor.equals(me) && !debtor.equals(me)) {
                    myPair.merge(debtor, -v, Double::sum);
                }
            }
        }
        List<Map<String, Object>> youOwe = new ArrayList<>();
        List<Map<String, Object>> youReceive = new ArrayList<>();
        for (var en : myPair.entrySet()) {
            if (en.getValue() > 0.001) {
                youOwe.add(Map.of("userId", en.getKey(), "name", nameOf(en.getKey()),
                        "amount", round(en.getValue())));
            } else if (en.getValue() < -0.001) {
                youReceive.add(Map.of("userId", en.getKey(), "name", nameOf(en.getKey()),
                        "amount", round(-en.getValue())));
            }
        }

        // Member balances: lifetime consumption net, adjusted for recorded
        // cash payments (PAID slices with a method, excluding payer shares).
        Map<Long, Double> share = new HashMap<>();
        Map<Long, Double> cashOut = new HashMap<>();
        Map<Long, Double> cashIn = new HashMap<>();
        for (ExpenseSplit s : rows) {
            share.merge(s.getUserId(), s.getAmount(), Double::sum);
            if (s.getSettledVia() != null) {
                TripExpense e = byId.get(s.getExpenseId());
                if (e != null && !s.getUserId().equals(e.getPaidByUserId())) {
                    cashOut.merge(s.getUserId(), s.getAmount(), Double::sum);
                    cashIn.merge(e.getPaidByUserId(), s.getAmount(), Double::sum);
                }
            }
        }
        Map<Long, Double> net = new HashMap<>();
        List<Map<String, Object>> memberBalances = new ArrayList<>();
        for (Long id : ids) {
            double p = paid.getOrDefault(id, 0.0);
            double o = share.getOrDefault(id, 0.0);
            double n = p - o + cashOut.getOrDefault(id, 0.0) - cashIn.getOrDefault(id, 0.0);
            net.put(id, n);
            memberBalances.add(Map.of(
                    "userId", id, "name", nameOf(id),
                    "totalPaid", round(p),
                    "totalOwed", round(o),
                    "net", round(n)));
        }

        // Global minimal plan from the outstanding (pending) person-net.
        Map<Long, Double> outstanding = new HashMap<>();
        for (Long id : ids) outstanding.put(id, 0.0);
        for (var en : pending.entrySet()) {
            for (var en2 : en.getValue().entrySet()) {
                outstanding.merge(en.getKey(), -en2.getValue(), Double::sum);
                outstanding.merge(en2.getKey(), en2.getValue(), Double::sum);
            }
        }
        List<Map<String, Object>> plan = settlePlan(outstanding);

        List<Map<String, Object>> payments = new ArrayList<>();
        for (ExpenseSplit s : rows) {
            if (s.getSettledVia() == null) continue;
            TripExpense e = byId.get(s.getExpenseId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("expenseId", s.getExpenseId());
            m.put("fromId", s.getUserId());
            m.put("from", nameOf(s.getUserId()));
            m.put("toId", e == null ? null : e.getPaidByUserId());
            m.put("to", e == null ? null : nameOf(e.getPaidByUserId()));
            m.put("amount", s.getAmount());
            m.put("method", s.getSettledVia());
            m.put("date", s.getUpdatedAt() == null ? null : s.getUpdatedAt().toString());
            payments.add(m);
            if (payments.size() >= 20) break;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalSpent", round(totalSpent));
        out.put("youOwe", youOwe);
        out.put("youReceive", youReceive);
        out.put("settlements", plan);
        out.put("members", memberBalances);
        out.put("payments", payments);
        return out;
    }

    /**
     * Records a payment between me and another member, marking the payer's
     * oldest PENDING splits (owed to the payee) PAID until amount is
     * exhausted. Partial final splits are sliced, totals preserved.
     */
    public Map<String, Object> recordPayment(
            Long tripId, Long me, Map<String, Object> body) {
        Long from = body.get("fromUserId") instanceof Number
                ? ((Number) body.get("fromUserId")).longValue() : me;
        Long to = body.get("toUserId") instanceof Number
                ? ((Number) body.get("toUserId")).longValue() : null;
        double amount = body.get("amount") instanceof Number
                ? ((Number) body.get("amount")).doubleValue() : 0;
        String method = body.get("method") == null
                ? "UPI" : body.get("method").toString().toUpperCase();
        if (to == null) throw new BadRequestException("toUserId required");
        if (from.equals(to)) throw new BadRequestException("Cannot pay yourself");
        if (!from.equals(me) && !to.equals(me)) {
            throw new BadRequestException("You must be a party to the payment");
        }
        if (amount <= 0) throw new BadRequestException("Amount must be positive");

        List<TripExpense> all = expenses.findByTripId(tripId);
        Map<Long, TripExpense> byId = new HashMap<>();
        List<Long> eids = new ArrayList<>();
        for (TripExpense e : all) {
            byId.put(e.getId(), e);
            eids.add(e.getId());
        }
        List<ExpenseSplit> rows = eids.isEmpty()
                ? List.of() : splits.findByExpenseIdIn(eids);
        List<ExpenseSplit> mine = new ArrayList<>();
        for (ExpenseSplit s : rows) {
            TripExpense e = byId.get(s.getExpenseId());
            if (e == null || !"PENDING".equals(s.getStatus())) continue;
            if (!s.getUserId().equals(from)) continue;
            if (!e.getPaidByUserId().equals(to)) continue;
            mine.add(s);
        }
        mine.sort(Comparator.comparing(ExpenseSplit::getId));
        double left = amount;
        double applied = 0;
        for (ExpenseSplit s : mine) {
            if (left <= 0.001) break;
            double share = s.getAmount() == null ? 0 : s.getAmount();
            if (left + 0.001 >= share) {
                left -= share;
                applied += share;
                s.setStatus("PAID");
                s.setSettledVia(method);
                splits.save(s);
            } else {
                // Partial: shrink the pending row, book the paid slice.
                s.setAmount(round(share - left));
                splits.save(s);
                ExpenseSplit paid = new ExpenseSplit();
                paid.setExpenseId(s.getExpenseId());
                paid.setUserId(s.getUserId());
                paid.setAmount(round(left));
                paid.setStatus("PAID");
                paid.setSettledVia(method);
                splits.save(paid);
                applied += left;
                left = 0;
            }
        }
        if (applied <= 0) throw new BadRequestException("Nothing pending to settle");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applied", round(applied));
        out.put("method", method);
        return out;
    }

    private List<Map<String, Object>> settlePlan(Map<Long, Double> net) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<Map.Entry<Long, Double>> debtors = new ArrayList<>();
        List<Map.Entry<Long, Double>> creditors = new ArrayList<>();
        for (var e : net.entrySet()) {
            double v = Math.round(e.getValue() * 100.0) / 100.0;
            if (v < -0.01) debtors.add(Map.entry(e.getKey(), -v));
            else if (v > 0.01) creditors.add(Map.entry(e.getKey(), v));
        }
        debtors.sort(Comparator.comparingDouble(Map.Entry<Long, Double>::getValue).reversed());
        creditors.sort(Comparator.comparingDouble(Map.Entry<Long, Double>::getValue).reversed());
        int di = 0, ci = 0;
        while (di < debtors.size() && ci < creditors.size()) {
            double settle = Math.min(debtors.get(di).getValue(), creditors.get(ci).getValue());
            if (settle > 0.01) {
                out.add(Map.of(
                        "fromId", debtors.get(di).getKey(),
                        "from", nameOf(debtors.get(di).getKey()),
                        "toId", creditors.get(ci).getKey(),
                        "to", nameOf(creditors.get(ci).getKey()),
                        "amount", Math.round(settle * 100.0) / 100.0));
            }
            double newD = debtors.get(di).getValue() - settle;
            double newC = creditors.get(ci).getValue() - settle;
            if (newD < 0.01) di++;
            else debtors.set(di, Map.entry(debtors.get(di).getKey(), newD));
            if (newC < 0.01) ci++;
            else creditors.set(ci, Map.entry(creditors.get(ci).getKey(), newC));
        }
        return out;
    }

    // ---------------- personal tracker ----------------

    public Map<String, Object> mySummary(Long userId) {
        List<TripExpense> mine = expenses.findByPaidByUserIdOrderByCreatedAtDesc(userId);
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        double monthPaid = 0;
        double totalPaid = 0;
        Map<String, Double> monthByCategory = new LinkedHashMap<>();
        List<Map<String, Object>> recent = new ArrayList<>();
        Map<Long, String> tripNames = new HashMap<>();
        for (TripExpense e : mine) {
            double a = e.getAmount() == null ? 0 : e.getAmount();
            totalPaid += a;
            boolean inMonth = false;
            if (e.getExpenseDate() != null && !e.getExpenseDate().isBefore(monthStart)) {
                monthPaid += a;
                inMonth = true;
            } else if (e.getExpenseDate() == null && e.getCreatedAt() != null
                    && !e.getCreatedAt().toLocalDate().isBefore(monthStart)) {
                monthPaid += a;
                inMonth = true;
            }
            if (inMonth) {
                monthByCategory.merge(e.getCategory(), a, Double::sum);
            }
            if (recent.size() < 8) {
                String tn = tripNames.computeIfAbsent(e.getTripId(),
                        id -> trips.findById(id).map(Trip::getTripName).orElse("Trip"));
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("expenseId", e.getId());
                m.put("tripId", e.getTripId());
                m.put("tripName", tn);
                m.put("title", e.getTitle());
                m.put("category", e.getCategory());
                m.put("amount", a);
                m.put("date", e.getExpenseDate() == null ? null : e.getExpenseDate().toString());
                recent.add(m);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("monthPaid", round(monthPaid));
        out.put("totalPaid", round(totalPaid));
        List<Map<String, Object>> catOut = new ArrayList<>();
        monthByCategory.entrySet().stream()
                .sorted((x, y) -> Double.compare(y.getValue(), x.getValue()))
                .forEach(en -> catOut.add(Map.of(
                        "category", en.getKey(), "amount", round(en.getValue()))));
        out.put("monthByCategory", catOut);
        out.put("recent", recent);
        return out;
    }

    // ---------------- share helpers ----------------

    /**
     * Computes userId -> share. MANUAL uses [{userId, amount}] (must total
     * the amount). EQUAL splits among the given ids, else all members.
     * PERSONAL assigns everything to the payer.
     */
    private Map<Long, Double> computeShares(
            String splitType, double amount, List<?> raw, Set<Long> ids) {
        Map<Long, Double> out = new LinkedHashMap<>();
        if ("PERSONAL".equals(splitType)) {
            return out;
        }
        if ("MANUAL".equals(splitType)) {
            if (raw == null || raw.isEmpty()) {
                throw new BadRequestException("Manual split needs per-member amounts");
            }
            double sum = 0;
            for (Object o : raw) {
                if (!(o instanceof Map)) continue;
                Map<?, ?> m = (Map<?, ?>) o;
                Long uid = toLong(m.get("userId"));
                Double a = toDouble(m.get("amount"));
                if (uid == null || a == null || a <= 0 || !ids.contains(uid)) continue;
                out.merge(uid, a, Double::sum);
                sum += a;
            }
            if (out.isEmpty() || Math.abs(sum - amount) > 0.01) {
                throw new BadRequestException("Manual split total must equal amount");
            }
            return out;
        }
        List<Long> who = new ArrayList<>();
        if (raw != null) {
            for (Object o : raw) {
                Long uid = toLong(o);
                if (uid != null && ids.contains(uid)) who.add(uid);
            }
        }
        if (who.isEmpty()) who.addAll(ids);
        if (who.isEmpty()) throw new BadRequestException("No members to split with");
        double share = amount / who.size();
        for (Long uid : who) out.put(uid, share);
        return out;
    }

    private void saveSplits(TripExpense e, Map<Long, Double> shares) {
        if ("PERSONAL".equals(e.getSplitType())) {
            ExpenseSplit s = new ExpenseSplit();
            s.setExpenseId(e.getId());
            s.setUserId(e.getPaidByUserId());
            s.setAmount(e.getAmount());
            s.setStatus("PAID");
            splits.save(s);
            return;
        }
        for (var en : shares.entrySet()) {
            ExpenseSplit s = new ExpenseSplit();
            s.setExpenseId(e.getId());
            s.setUserId(en.getKey());
            s.setAmount(en.getValue());
            s.setStatus(en.getKey().equals(e.getPaidByUserId()) ? "PAID" : "PENDING");
            splits.save(s);
        }
    }

    private void retireSplits(Long expenseId) {
        for (ExpenseSplit s : splits.findByExpenseId(expenseId)) {
            s.softDelete();
            splits.save(s);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<?> asList(Object v) {
        if (v instanceof List) return (List<?>) v;
        return List.of();
    }

    private static LocalDate parseDate(Object v) {
        if (v == null) return null;
        try {
            return LocalDate.parse(v.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static Long toLong(Object o) {
        if (o instanceof Number) return ((Number) o).longValue();
        try {
            return o == null ? null : Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double toDouble(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            return o == null ? null : Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
