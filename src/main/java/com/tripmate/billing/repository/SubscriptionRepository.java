package com.tripmate.billing.repository;

import com.tripmate.billing.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByCode(String code);

    List<Subscription> findAllByOrderBySortOrderAsc();
}
