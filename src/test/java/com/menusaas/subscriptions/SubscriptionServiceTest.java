package com.menusaas.subscriptions;

import com.menusaas.config.AppProperties;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.shared.security.SecurityUtils;
import com.menusaas.subscriptions.dto.SubscribeResult;
import com.menusaas.subscriptions.dto.SubscriptionRequest;
import com.menusaas.subscriptions.dto.SubscriptionResponse;
import com.menusaas.subscriptions.entity.Plan;
import com.menusaas.subscriptions.entity.Subscription;
import com.menusaas.subscriptions.payment.ManualPaymentGateway;
import com.menusaas.subscriptions.repository.PlanRepository;
import com.menusaas.subscriptions.repository.SubscriptionRepository;
import com.menusaas.subscriptions.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Ciclo de vida de suscripciones: activación (modo manual), cancelación,
 * expiración automática y activación por webhook.
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PlanRepository planRepository;

    private AppProperties appProperties;
    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties(
                new AppProperties.Jwt("c2VjcmV0by1kZS1wcnVlYmEtc2VndXJvLWxvbmctZW5vdWdoLXNlY3JldA==", 15, 7),
                new AppProperties.Cors(java.util.List.of("http://localhost:4200")),
                "http://localhost:4200", "http://localhost:8080", "./uploads",
                new AppProperties.Security(false, 3600), new AppProperties.Payments("", "", "", ""));
        service = new SubscriptionService(subscriptionRepository, planRepository,
                new ManualPaymentGateway(appProperties), appProperties);
    }

    private Plan plan(String code, String price) {
        return Plan.builder().id(1L).code(code).name("Plan").priceMonthly(new BigDecimal(price)).active(true).build();
    }

    @Test
    void subscribe_manualMode_activatesImmediately_forPaidPlan() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan("PRO", "29900")));
            when(subscriptionRepository.findFirstByRestaurantIdAndStatusOrderByCreatedAtDesc(1L, Subscription.STATUS_PENDING))
                    .thenReturn(Optional.empty());
            when(subscriptionRepository.findByRestaurantIdAndStatusInOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(List.of());
            when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> {
                Subscription s = inv.getArgument(0);
                s.setId(10L);
                return s;
            });
            when(planRepository.findById(1L)).thenReturn(Optional.of(plan("PRO", "29900")));

            SubscribeResult result = service.subscribe(new SubscriptionRequest("PRO"));

            assertThat(result.checkoutSessionId()).isNull();
            SubscriptionResponse response = result.subscription();
            assertThat(response.status()).isEqualTo(Subscription.STATUS_ACTIVE);
            assertThat(response.endsAt()).isNotNull(); // plan de pago: período de 30 días
        }
    }

    @Test
    void subscribe_manualMode_freePlan_hasNoExpiration() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            when(planRepository.findByCode("FREE")).thenReturn(Optional.of(plan("FREE", "0")));
            when(subscriptionRepository.findFirstByRestaurantIdAndStatusOrderByCreatedAtDesc(1L, Subscription.STATUS_PENDING))
                    .thenReturn(Optional.empty());
            when(subscriptionRepository.findByRestaurantIdAndStatusInOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(List.of());
            when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> {
                Subscription s = inv.getArgument(0);
                s.setId(11L);
                return s;
            });
            when(planRepository.findById(1L)).thenReturn(Optional.of(plan("FREE", "0")));

            SubscriptionResponse response = service.subscribe(new SubscriptionRequest("FREE")).subscription();

            assertThat(response.status()).isEqualTo(Subscription.STATUS_ACTIVE);
            assertThat(response.endsAt()).isNull();
        }
    }

    @Test
    void subscribe_unknownPlan_throws404() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            when(planRepository.findByCode("NOEXISTE")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.subscribe(new SubscriptionRequest("NOEXISTE")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    void cancelMySubscription_marksCancelled() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            Subscription active = Subscription.builder().id(5L).restaurantId(1L).planId(1L)
                    .status(Subscription.STATUS_ACTIVE).startsAt(Instant.now()).build();
            when(subscriptionRepository.findFirstByRestaurantIdAndStatusOrderByCreatedAtDesc(1L, Subscription.STATUS_ACTIVE))
                    .thenReturn(Optional.of(active));
            when(subscriptionRepository.save(any(Subscription.class))).thenReturn(active);
            when(planRepository.findById(1L)).thenReturn(Optional.of(plan("PRO", "29900")));

            SubscriptionResponse response = service.cancelMySubscription();

            assertThat(response.status()).isEqualTo(Subscription.STATUS_CANCELLED);
            assertThat(response.endsAt()).isNotNull();
        }
    }

    @Test
    void expireDueSubscriptions_marksExpired_OnlyWhenEndsAtPassed() {
        Subscription due = Subscription.builder().id(1L).restaurantId(1L).planId(1L)
                .status(Subscription.STATUS_ACTIVE)
                .startsAt(Instant.now().minusSeconds(4000))
                .endsAt(Instant.now().minusSeconds(60))
                .build();
        Subscription future = Subscription.builder().id(2L).restaurantId(2L).planId(1L)
                .status(Subscription.STATUS_ACTIVE)
                .startsAt(Instant.now())
                .endsAt(Instant.now().plusSeconds(4000))
                .build();
        // La query ya filtra por ends_at < ahora: solo "due" es candidata.
        when(subscriptionRepository.findByStatusAndEndsAtBefore(eq(Subscription.STATUS_ACTIVE), any(Instant.class)))
                .thenReturn(List.of(due));

        service.expireDueSubscriptions();

        assertThat(due.getStatus()).isEqualTo(Subscription.STATUS_EXPIRED);
        assertThat(future.getStatus()).isEqualTo(Subscription.STATUS_ACTIVE);
        verify(subscriptionRepository, times(1)).save(due);
        verify(subscriptionRepository, never()).save(future);
    }

    @Test
    void activateFromGateway_activatesSubscription_fromWebhook() {
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(plan("PRO", "29900")));
        when(subscriptionRepository.findByRestaurantIdAndStatusInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> {
            Subscription s = inv.getArgument(0);
            s.setId(20L);
            return s;
        });
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan("PRO", "29900")));

        SubscriptionResponse response = service.activateFromGateway(
                1L, "PRO", "sub_123", Instant.now().plusSeconds(86400));

        assertThat(response.status()).isEqualTo(Subscription.STATUS_ACTIVE);
        assertThat(response.provider()).isEqualTo(Subscription.PROVIDER_EPAYCO);
        assertThat(response.providerReference()).isEqualTo("sub_123");
        assertThat(response.endsAt()).isNotNull();
    }

    @Test
    void getMySubscription_withoutActive_throws404() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            when(subscriptionRepository.findFirstByRestaurantIdAndStatusOrderByCreatedAtDesc(1L, Subscription.STATUS_ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(service::getMySubscription)
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}