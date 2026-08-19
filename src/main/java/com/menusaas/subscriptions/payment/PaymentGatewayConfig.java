package com.menusaas.subscriptions.payment;

import com.menusaas.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selecciona la pasarela de pagos de forma determinista:
 * - Con STRIPE_SECRET_KEY configurado → Stripe.
 * - Sin claves → modo manual (solo dev/pruebas; en prod se debe configurar Stripe).
 */
@Configuration
public class PaymentGatewayConfig {

    @Bean
    public PaymentGateway paymentGateway(AppProperties appProperties) {
        String secretKey = appProperties.payments().stripeSecretKey();
        if (secretKey != null && !secretKey.isBlank()) {
            return new StripePaymentGateway(appProperties);
        }
        return new ManualPaymentGateway(appProperties);
    }
}