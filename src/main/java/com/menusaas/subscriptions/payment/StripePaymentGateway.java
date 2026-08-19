package com.menusaas.subscriptions.payment;

import com.menusaas.config.AppProperties;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.subscriptions.entity.Plan;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Pasarela Stripe: Checkout Session (suscripción mensual) + verificación de
 * webhooks con firma. Se activa cuando existe STRIPE_SECRET_KEY.
 */
@Slf4j
public class StripePaymentGateway implements PaymentGateway {

    private static final String CURRENCY = "cop";
    private static final String METADATA_RESTAURANT_ID = "restaurantId";
    private static final String METADATA_PLAN_CODE = "planCode";

    private final AppProperties appProperties;

    public StripePaymentGateway(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostConstruct
    void init() {
        Stripe.apiKey = appProperties.payments().stripeSecretKey();
    }

    @Override
    public boolean isConfigured() {
        return appProperties.payments().stripeSecretKey() != null
                && !appProperties.payments().stripeSecretKey().isBlank();
    }

    @Override
    public CheckoutSession createCheckout(Long restaurantId, Plan plan, String successUrl, String cancelUrl) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put(METADATA_RESTAURANT_ID, String.valueOf(restaurantId));
            metadata.put(METADATA_PLAN_CODE, plan.getCode());

            Map<String, Object> priceData = new HashMap<>();
            priceData.put("currency", CURRENCY);
            priceData.put("unit_amount", plan.getPriceMonthly().movePointRight(2).longValueExact());
            priceData.put("product_data", Map.of(
                    "name", "Plan " + plan.getName(),
                    "description", plan.getDescription() != null ? plan.getDescription() : "Suscripción mensual",
                    "tax_code", "txcd_99999999"
            ));

            Map<String, Object> lineItems = new HashMap<>();
            lineItems.put("quantity", 1L);
            lineItems.put("price_data", priceData);

            Map<String, Object> params = new HashMap<>();
            params.put("mode", "subscription");
            params.put("line_items", java.util.List.of(lineItems));
            params.put("success_url", successUrl);
            params.put("cancel_url", cancelUrl);
            params.put("metadata", metadata);
            params.put("allow_promotion_codes", true);

            Session session = Session.create(params);
            return new CheckoutSession(session.getId(), session.getUrl());
        } catch (StripeException ex) {
            log.error("Error creando sesión de Stripe", ex);
            throw new IllegalStateException("No se pudo iniciar el pago: " + ex.getMessage(), ex);
        }
    }

    @Override
    public PaymentEvent handleWebhook(String payload, String signatureHeader) {
        String secret = appProperties.payments().stripeWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new BadRequestException("STRIPE_WEBHOOK_SECRET no configurado");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, secret);
        } catch (SignatureVerificationException ex) {
            throw new BadRequestException("Firma de webhook inválida");
        }

        return switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutCompleted(event);
            case "customer.subscription.deleted" -> handleSubscriptionCancelled(event);
            default -> {
                log.info("Evento Stripe ignorado: {}", event.getType());
                yield null;
            }
        };
    }

    private PaymentEvent handleCheckoutCompleted(Event event) {
        Session session = event.getDataObjectDeserializer()
                .getObject()
                .filter(Session.class::isInstance)
                .map(Session.class::cast)
                .orElse(null);
        if (session == null || !"paid".equals(session.getPaymentStatus())) {
            return null;
        }
        Map<String, String> metadata = session.getMetadata();
        Long restaurantId = metadata == null ? null : Long.valueOf(metadata.getOrDefault(METADATA_RESTAURANT_ID, "0"));
        String planCode = metadata == null ? null : metadata.get(METADATA_PLAN_CODE);
        Instant periodEnd = session.getSubscription() != null
                ? subscriptionCurrentPeriodEnd(session.getSubscription()) : null;
        return new PaymentEvent(
                PaymentEvent.TYPE_CHECKOUT_COMPLETED,
                session.getSubscription(),
                restaurantId, planCode, periodEnd);
    }

    private PaymentEvent handleSubscriptionCancelled(Event event) {
        com.stripe.model.Subscription subscription = event.getDataObjectDeserializer()
                .getObject()
                .filter(com.stripe.model.Subscription.class::isInstance)
                .map(com.stripe.model.Subscription.class::cast)
                .orElse(null);
        if (subscription == null) {
            return null;
        }
        Map<String, String> metadata = subscription.getMetadata();
        Long restaurantId = metadata == null ? null : Long.valueOf(metadata.getOrDefault(METADATA_RESTAURANT_ID, "0"));
        return new PaymentEvent(
                PaymentEvent.TYPE_SUBSCRIPTION_CANCELLED,
                subscription.getId(),
                restaurantId, null, null);
    }

    private Instant subscriptionCurrentPeriodEnd(String subscriptionId) {
        try {
            com.stripe.model.Subscription subscription =
                    com.stripe.model.Subscription.retrieve(subscriptionId);
            Long end = subscription.getItems() != null
                    && subscription.getItems().getData() != null
                    && !subscription.getItems().getData().isEmpty()
                    ? subscription.getItems().getData().get(0).getCurrentPeriodEnd()
                    : null;
            return end != null ? Instant.ofEpochSecond(end) : null;
        } catch (StripeException ex) {
            log.warn("No se pudo obtener el período de la suscripción {}", subscriptionId, ex);
            return null;
        }
    }
}