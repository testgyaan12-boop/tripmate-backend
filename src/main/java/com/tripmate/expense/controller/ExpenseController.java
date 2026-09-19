package com.tripmate.expense.controller;

import com.tripmate.common.dto.ApiResponse;
import com.tripmate.common.exception.BadRequestException;
import com.tripmate.expense.service.BudgetService;
import com.tripmate.expense.service.ExpenseService;
import com.tripmate.member.repository.TripMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;
    private final BudgetService budgetService;
    private final TripMemberRepository members;

    private Long me() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    private void requireMember(Long tripId) {
        if (!members.existsByTripIdAndUserId(tripId, me())) {
            throw new BadRequestException("Not a trip member");
        }
    }

    @PostMapping("/api/trips/{tripId}/expenses")
    public ApiResponse<?> create(@PathVariable Long tripId,
                                 @RequestBody Map<String, Object> body) {
        requireMember(tripId);
        return ApiResponse.ok(expenseService.create(tripId, me(), body));
    }

    @GetMapping("/api/trips/{tripId}/expenses")
    public ApiResponse<?> list(@PathVariable Long tripId) {
        requireMember(tripId);
        return ApiResponse.ok(expenseService.listByTrip(tripId));
    }

    @PutMapping("/api/trips/{tripId}/expenses/{expenseId}")
    public ApiResponse<?> update(@PathVariable Long tripId,
                                 @PathVariable Long expenseId,
                                 @RequestBody Map<String, Object> body) {
        requireMember(tripId);
        return ApiResponse.ok(expenseService.update(tripId, expenseId, body));
    }

    @DeleteMapping("/api/trips/{tripId}/expenses/{expenseId}")
    public ApiResponse<?> delete(@PathVariable Long tripId,
                                 @PathVariable Long expenseId) {
        requireMember(tripId);
        expenseService.delete(tripId, expenseId);
        return ApiResponse.ok("Deleted");
    }

    @PatchMapping("/api/trips/{tripId}/expenses/{expenseId}/settle")
    public ApiResponse<?> markSettled(@PathVariable Long tripId,
                                      @PathVariable Long expenseId,
                                      @RequestBody Map<String, Object> body) {
        requireMember(tripId);
        boolean settled = Boolean.TRUE.equals(body.get("settled"))
                || "true".equalsIgnoreCase(String.valueOf(body.get("settled")));
        return ApiResponse.ok(expenseService.markSettled(tripId, expenseId, settled));
    }

    @GetMapping("/api/trips/{tripId}/settlements")
    public ApiResponse<?> settlements(@PathVariable Long tripId) {
        requireMember(tripId);
        return ApiResponse.ok(expenseService.settlements(tripId, me()));
    }

    @PostMapping("/api/trips/{tripId}/settlements/record")
    public ApiResponse<?> recordPayment(@PathVariable Long tripId,
                                        @RequestBody Map<String, Object> body) {
        requireMember(tripId);
        return ApiResponse.ok(expenseService.recordPayment(tripId, me(), body));
    }

    @GetMapping("/api/trips/{tripId}/budget")
    public ApiResponse<?> budget(@PathVariable Long tripId) {
        requireMember(tripId);
        return ApiResponse.ok(budgetService.getBudget(tripId));
    }

    @PutMapping("/api/trips/{tripId}/budget")
    public ApiResponse<?> saveBudget(@PathVariable Long tripId,
                                     @RequestBody Map<String, Object> body) {
        requireMember(tripId);
        return ApiResponse.ok(budgetService.saveBudget(tripId, body));
    }

    @GetMapping("/api/users/me/expenses/summary")
    public ApiResponse<?> mySummary() {
        return ApiResponse.ok(expenseService.mySummary(me()));
    }
}
