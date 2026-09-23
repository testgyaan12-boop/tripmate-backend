package com.tripmate.billing.repository;

import com.tripmate.billing.entity.PlanMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanMasterRepository extends JpaRepository<PlanMaster, Long> {

    Optional<PlanMaster> findByPlanCode(String planCode);

    List<PlanMaster> findAllByOrderBySortOrderAsc();
}
