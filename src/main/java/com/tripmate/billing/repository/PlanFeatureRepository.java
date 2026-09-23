package com.tripmate.billing.repository;

import com.tripmate.billing.entity.PlanFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanFeatureRepository extends JpaRepository<PlanFeature, Long> {

    List<PlanFeature> findByPlanId(Long planId);
}
