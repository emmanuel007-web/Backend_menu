package com.menusaas.subscriptions.payment;

import com.menusaas.subscriptions.entity.Plan;

import java.time.Instant;

/**
 * Abstracción de pasarela de pago. Con STRIPE_SECRET_KEY configurado se usa
 * Stripe (Checkout + webhooks); sin claves se opera en modo manual (solo dev).
 */
public interface PaymentGateway {

    boolean isConfigured();

    /**
     * Crea un checkout y devuelve la URL a la que redirigir al cliente.
     */
    CheckoutSession createCheckout(Long restaurantId, Plan plan, String successUrl, String cancelUrl);

    /**
     * Procesa un webhook firmado y devuelve el evento de pago resultante.
     */
    PaymentEvent handleWebhook(String payload, String signatureHeader);

    record CheckoutSession(String id, String url) {
    }

    record PaymentEvent(String type, String providerReference, Long restaurantId, String planCode, Instant periodEnd) {

        public static final String TYPE_CHECKOUT_COMPLETED = "CHECKOUT_COMPLETED";
        public static final String TYPE_SUBSCRIPTION_CANCELLED = "SUBSCRIPTION_CANCELLED";
    }
}