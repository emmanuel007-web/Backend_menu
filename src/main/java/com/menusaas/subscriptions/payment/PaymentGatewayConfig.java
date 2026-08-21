package com.menusaas.subscriptions.payment;

import com.menusaas.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selecciona la pasarela de pagos de forma determinista:
 * - Con EPAYCO_PUBLIC_KEY configurado → ePayco Smart Checkout v2.
 * - Sin claves → modo manual (solo dev/pruebas; en prod se debe configurar ePayco).
 */
@Configuration
public class PaymentGatewayConfig {

    @Bean
    public PaymentGateway paymentGateway(AppProperties appProperties) {
        String publicKey = appProperties.payments().epaycoPublicKey();
        if (publicKey != null && !publicKey.isBlank()) {
            return new EpaycoPaymentGateway(appProperties);
        }
        return new ManualPaymentGateway(appProperties);
    }
}