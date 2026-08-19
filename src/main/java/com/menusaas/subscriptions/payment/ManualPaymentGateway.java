package com.menusaas.subscriptions.payment;

import com.menusaas.config.AppProperties;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.subscriptions.entity.Plan;

import java.time.Instant;

/**
 * Pasarela en modo manual: activa la suscripción sin cobro.
 * Se usa ÚNICAMENTE cuando no se han configurado las claves de Stripe (dev/tests).
 * En producción esto no debería ocurrir: si se desea cobrar, defina STRIPE_SECRET_KEY.
 */
public class ManualPaymentGateway implements PaymentGateway {

    private final AppProperties appProperties;

    public ManualPaymentGateway(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public CheckoutSession createCheckout(Long restaurantId, Plan plan, String successUrl, String cancelUrl) {
        throw new BadRequestException(
                "La pasarela de pagos no está configurada. Defina STRIPE_SECRET_KEY en el servidor.");
    }

    @Override
    public PaymentEvent handleWebhook(String payload, String signatureHeader) {
        throw new BadRequestException("No hay pasarela de pagos configurada para recibir webhooks");
    }
}