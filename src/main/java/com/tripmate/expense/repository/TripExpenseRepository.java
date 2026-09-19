package com.tripmate.expense.repository;

import com.tripmate.expense.entity.TripExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TripExpenseRepository extends JpaRepository<TripExpense, Long> {
    List<TripExpense> findByTripId(Long tripId);

    List<TripExpense> findByPaidByUserIdOrderByCreatedAtDesc(Long paidByUserId);
}
