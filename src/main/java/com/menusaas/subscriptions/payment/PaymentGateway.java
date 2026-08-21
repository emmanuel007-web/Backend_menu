package com.menusaas.subscriptions.payment;

import com.menusaas.subscriptions.entity.Plan;

import java.time.Instant;

/**
 * Abstracción de pasarela de pago. Con EPAYCO_PUBLIC_KEY configurado se usa
 * ePayco Smart Checkout v2; sin claves se opera en modo manual (solo dev).
 */
public interface PaymentGateway {

    boolean isConfigured();

    /**
     * Crea una sesión de checkout y devuelve el sessionId + token para
     * inicializar el Smart Checkout en el frontend.
     */
    CheckoutSession createCheckout(Long restaurantId, Plan plan, String confirmationUrl, String responseUrl);

    /**
     * Procesa un webhook de confirmación y devuelve el evento de pago resultante.
     */
    PaymentEvent handleWebhook(java.util.Map<String, String> params);

    record CheckoutSession(String sessionId, String token) {
    }

    record PaymentEvent(String type, String providerReference, Long restaurantId, String planCode, Instant periodEnd) {

        public static final String TYPE_CHECKOUT_COMPLETED = "CHECKOUT_COMPLETED";
        public static final String TYPE_SUBSCRIPTION_CANCELLED = "SUBSCRIPTION_CANCELLED";
    }
}