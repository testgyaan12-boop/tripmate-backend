package com.tripmate.billing.repository;

import com.tripmate.billing.entity.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

    Optional<UserSubscription> findFirstByUserIdAndStatusOrderByEndDateDesc(Long userId, String status);

    Optional<UserSubscription> findFirstByUserIdAndPlanIdAndStatusOrderByEndDateDesc(
            Long userId, Long planId, String status);

    Optional<UserSubscription> findByGatewaySubscriptionId(String gatewaySubscriptionId);
}
