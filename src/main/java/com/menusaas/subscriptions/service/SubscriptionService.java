package com.menusaas.subscriptions.service;

import com.menusaas.config.AppProperties;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.shared.security.SecurityUtils;
import com.menusaas.subscriptions.dto.PlanResponse;
import com.menusaas.subscriptions.dto.SubscribeResult;
import com.menusaas.subscriptions.dto.SubscriptionRequest;
import com.menusaas.subscriptions.dto.SubscriptionResponse;
import com.menusaas.subscriptions.entity.Plan;
import com.menusaas.subscriptions.entity.Subscription;
import com.menusaas.subscriptions.payment.PaymentGateway;
import com.menusaas.subscriptions.repository.PlanRepository;
import com.menusaas.subscriptions.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Ciclo de vida de suscripciones:
 * - Suscribirse a un plan (vía pasarela ePayco, o directo en modo manual).
 * - Cancelación.
 * - Expiración automática (job por hora) cuando ends_at pasa.
 * - Activación/confirmación desde webhooks de la pasarela.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final PaymentGateway paymentGateway;
    private final AppProperties appProperties;

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

    /**
     * Solicitud de suscripción/cambio de plan.
     * - Con pasarela configurada: crea una sesión de ePayco Smart Checkout y deja la
     *   suscripción en PENDING; se activa cuando llega el webhook.
     * - Sin pasarela (modo manual/dev): se activa al instante.
     */
    @Transactional
    public SubscribeResult subscribe(SubscriptionRequest request) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        Plan plan = planRepository.findByCode(request.planCode())
                .filter(Plan::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Plan no encontrado"));

        Subscription pending = subscriptionRepository.findFirstByRestaurantIdAndStatusOrderByCreatedAtDesc(
                        restaurantId, Subscription.STATUS_PENDING)
                .orElseGet(() -> {
                    Subscription created = Subscription.builder()
                            .restaurantId(restaurantId)
                            .planId(plan.getId())
                            .status(Subscription.STATUS_PENDING)
                            .provider(paymentGateway.isConfigured()
                                    ? Subscription.PROVIDER_EPAYCO
                                    : Subscription.PROVIDER_MANUAL)
                            .startsAt(Instant.now())
                            .build();
                    return subscriptionRepository.save(created);
                });
        pending.setPlanId(plan.getId());

        if (paymentGateway.isConfigured()) {
            String base = appProperties.appBaseUrl();
            String confirmationUrl = appProperties.apiBaseUrl() + "/api/webhooks/epayco";
            String responseUrl = base + "/admin/settings?checkout=done";
            PaymentGateway.CheckoutSession session = paymentGateway.createCheckout(
                    restaurantId, plan, confirmationUrl, responseUrl);
            pending.setProvider(Subscription.PROVIDER_EPAYCO);
            pending.setProviderReference(session.sessionId());
            subscriptionRepository.save(pending);
            return new SubscribeResult(toResponse(pending), session.sessionId());
        }

        // Modo manual (sin pasarela): activación inmediata.
        return new SubscribeResult(activate(restaurantId, plan, Subscription.PROVIDER_MANUAL, null), null);
    }

    @Transactional
    public SubscriptionResponse cancelMySubscription() {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        Subscription active = subscriptionRepository
                .findFirstByRestaurantIdAndStatusOrderByCreatedAtDesc(restaurantId, Subscription.STATUS_ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No hay una suscripción activa"));
        active.setStatus(Subscription.STATUS_CANCELLED);
        if (active.getEndsAt() == null) {
            active.setEndsAt(Instant.now());
        }
        return toResponse(subscriptionRepository.save(active));
    }

    /**
     * Confirma una suscripción desde la pasarela (webhook checkout.session.completed).
     */
    @Transactional
    public SubscriptionResponse activateFromGateway(Long restaurantId, String planCode,
                                                    String providerReference, Instant periodEnd) {
        if (restaurantId == null || planCode == null) {
            throw new BadRequestException("Webhook sin datos de restaurante/plan");
        }
        Plan plan = planRepository.findByCode(planCode)
                .filter(Plan::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Plan no encontrado en el webhook"));

        // Cancela cualquier suscripción activa/pendiente previa del restaurante.
        subscriptionRepository.findByRestaurantIdAndStatusInOrderByCreatedAtDesc(
                        restaurantId, List.of(Subscription.STATUS_ACTIVE, Subscription.STATUS_PENDING))
                .forEach(s -> s.setStatus(Subscription.STATUS_CANCELLED));

        Subscription subscription = Subscription.builder()
                .restaurantId(restaurantId)
                .planId(plan.getId())
                .status(Subscription.STATUS_ACTIVE)
                .provider(Subscription.PROVIDER_EPAYCO)
                .providerReference(providerReference)
                .startsAt(Instant.now())
                .endsAt(periodEnd)
                .build();
        return toResponse(subscriptionRepository.save(subscription));
    }

    /**
     * Procesa la cancelación reportada por la pasarela (customer.subscription.deleted).
     */
    @Transactional
    public void cancelFromGateway(Long restaurantId, String providerReference) {
        if (providerReference == null || providerReference.isBlank()) {
            return;
        }
        subscriptionRepository.findByProviderReference(providerReference)
                .ifPresent(sub -> {
                    sub.setStatus(Subscription.STATUS_CANCELLED);
                    if (sub.getEndsAt() == null) {
                        sub.setEndsAt(Instant.now());
                    }
                    subscriptionRepository.save(sub);
                });
    }

    /**
     * Expiración automática: suscripciones ACTIVE cuya fecha de fin ya pasó.
     */
    @Transactional
    @Scheduled(cron = "0 5 * * * *", zone = "UTC")
    public void expireDueSubscriptions() {
        List<Subscription> due = subscriptionRepository
                .findByStatusAndEndsAtBefore(Subscription.STATUS_ACTIVE, Instant.now());
        for (Subscription subscription : due) {
            subscription.setStatus(Subscription.STATUS_EXPIRED);
            subscriptionRepository.save(subscription);
            log.info("Suscripción expirada: id={}, restaurante={}", subscription.getId(), subscription.getRestaurantId());
        }
    }

    /**
     * Activa la suscripción (modo manual o confirmación local).
     */
    private SubscriptionResponse activate(Long restaurantId, Plan plan, String provider, String providerReference) {
        subscriptionRepository.findByRestaurantIdAndStatusInOrderByCreatedAtDesc(
                        restaurantId, List.of(Subscription.STATUS_ACTIVE, Subscription.STATUS_PENDING))
                .forEach(s -> s.setStatus(Subscription.STATUS_CANCELLED));

        Subscription subscription = Subscription.builder()
                .restaurantId(restaurantId)
                .planId(plan.getId())
                .status(Subscription.STATUS_ACTIVE)
                .provider(provider)
                .providerReference(providerReference)
                .startsAt(Instant.now())
                .endsAt(plan.getPriceMonthly().signum() > 0 ? Instant.now().plusSeconds(30L * 86400) : null)
                .build();
        return toResponse(subscriptionRepository.save(subscription));
    }

    private SubscriptionResponse toResponse(Subscription s) {
        Plan plan = planRepository.findById(s.getPlanId())
                .orElseThrow(() -> new IllegalStateException("Plan de la suscripción no existe"));
        return new SubscriptionResponse(
                s.getId(), s.getRestaurantId(), toPlanResponse(plan),
                s.getStatus(), s.getProvider(), s.getProviderReference(),
                s.getStartsAt(), s.getEndsAt()
        );
    }

    private PlanResponse toPlanResponse(Plan p) {
        return new PlanResponse(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getPriceMonthly());
    }
}