package com.menusaas.subscriptions.repository;

import com.menusaas.subscriptions.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    List<Plan> findByActiveTrueOrderByPriceMonthlyAsc();

    Optional<Plan> findByCode(String code);
}