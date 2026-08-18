package com.menusaas.subscriptions.service;

import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.shared.security.SecurityUtils;
import com.menusaas.subscriptions.dto.PlanResponse;
import com.menusaas.subscriptions.dto.SubscriptionRequest;
import com.menusaas.subscriptions.dto.SubscriptionResponse;
import com.menusaas.subscriptions.entity.Plan;
import com.menusaas.subscriptions.entity.Subscription;
import com.menusaas.subscriptions.repository.PlanRepository;
import com.menusaas.subscriptions.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * MVP: gestión manual de suscripciones (sin pasarela de pago todavía).
 * El restaurante activa un plan; posteriormente se integrará la pasarela.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;

    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans() {
        return planRepository.findByActiveTrueOrderByPriceMonthlyAsc()
                .stream()
                .map(this::toPlanResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getMySubscription() {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        return subscriptionRepository.findFirstByRestaurantIdAndStatusOrderByCreatedAtDesc(restaurantId, Subscription.STATUS_ACTIVE)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("El restaurante no tiene una suscripción activa"));
    }

    @Transactional
    public SubscriptionResponse subscribe(SubscriptionRequest request) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        Plan plan = planRepository.findByCode(request.planCode())
                .filter(Plan::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Plan no encontrado"));

        subscriptionRepository.findFirstByRestaurantIdAndStatusOrderByCreatedAtDesc(restaurantId, Subscription.STATUS_ACTIVE)
                .ifPresent(s -> s.setStatus(Subscription.STATUS_CANCELLED));

        Subscription subscription = Subscription.builder()
                .restaurantId(restaurantId)
                .planId(plan.getId())
                .status(Subscription.STATUS_ACTIVE)
                .startsAt(Instant.now())
                .build();
        return toResponse(subscriptionRepository.save(subscription));
    }

    private SubscriptionResponse toResponse(Subscription s) {
        Plan plan = planRepository.findById(s.getPlanId())
                .orElseThrow(() -> new IllegalStateException("Plan de la suscripción no existe"));
        return new SubscriptionResponse(
                s.getId(), s.getRestaurantId(), toPlanResponse(plan),
                s.getStatus(), s.getStartsAt(), s.getEndsAt()
        );
    }

    private PlanResponse toPlanResponse(Plan p) {
        return new PlanResponse(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getPriceMonthly());
    }
}