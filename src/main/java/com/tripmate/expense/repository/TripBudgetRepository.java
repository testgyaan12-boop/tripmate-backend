package com.tripmate.expense.repository;

import com.tripmate.expense.entity.TripBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TripBudgetRepository extends JpaRepository<TripBudget, Long> {
    List<TripBudget> findByTripId(Long tripId);

    Optional<TripBudget> findByTripIdAndCategory(Long tripId, String category);
}
