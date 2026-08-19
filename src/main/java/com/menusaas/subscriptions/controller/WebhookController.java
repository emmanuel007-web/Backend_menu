package com.menusaas.subscriptions.controller;

import com.menusaas.shared.api.ApiResponse;
import com.menusaas.subscriptions.payment.PaymentGateway;
import com.menusaas.subscriptions.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Webhooks de pasarela de pagos. Autenticación por FIRMA criptográfica del
 * proveedor (jamás por sesión), por eso está en permitAll de seguridad.
 */
@Tag(name = "Webhooks", description = "Webhooks de pasarela de pagos (firma verificada)")
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final PaymentGateway paymentGateway;
    private final SubscriptionService subscriptionService;

    @Operation(summary = "Webhook de Stripe (verifica firma + procesa el evento)")
    @PostMapping("/stripe")
    public ApiResponse<Void> stripe(@RequestBody String payload,
                                    @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        PaymentGateway.PaymentEvent event = paymentGateway.handleWebhook(payload, signature);
        if (event != null) {
            switch (event.type()) {
                case PaymentGateway.PaymentEvent.TYPE_CHECKOUT_COMPLETED ->
                        subscriptionService.activateFromGateway(
                                event.restaurantId(), event.planCode(), event.providerReference(), event.periodEnd());
                case PaymentGateway.PaymentEvent.TYPE_SUBSCRIPTION_CANCELLED ->
                        subscriptionService.cancelFromGateway(event.restaurantId(), event.providerReference());
                default -> {
                }
            }
        }
        return ApiResponse.ok("Webhook procesado");
    }
}