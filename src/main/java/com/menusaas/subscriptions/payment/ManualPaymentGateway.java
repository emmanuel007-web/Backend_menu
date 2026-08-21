package com.menusaas.subscriptions.payment;

import com.menusaas.config.AppProperties;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.subscriptions.entity.Plan;

import java.util.Map;

/**
 * Pasarela en modo manual: activa la suscripción sin cobro.
 * Se usa ÚNICAMENTE cuando no se han configurado las claves de ePayco (dev/tests).
 * En producción esto no debería ocurrir: si se desea cobrar, defina EPAYCO_PUBLIC_KEY.
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
    public CheckoutSession createCheckout(Long restaurantId, Plan plan, String confirmationUrl, String responseUrl) {
        throw new BadRequestException(
                "La pasarela de pagos no está configurada. Defina EPAYCO_PUBLIC_KEY en el servidor.");
    }

    @Override
    public PaymentEvent handleWebhook(Map<String, String> params) {
        throw new BadRequestException("No hay pasarela de pagos configurada para recibir webhooks");
    }
}