package com.menusaas.subscriptions.controller;

import com.menusaas.shared.api.ApiResponse;
import com.menusaas.subscriptions.payment.PaymentGateway;
import com.menusaas.subscriptions.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Webhooks de pasarela de pagos. Autenticación por FIRMA criptográfica del
 * proveedor (jamás por sesión), por eso está en permitAll de seguridad.
 */
@Slf4j
@Tag(name = "Webhooks", description = "Webhooks de pasarela de pagos (firma verificada)")
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final PaymentGateway paymentGateway;
    private final SubscriptionService subscriptionService;

    @Operation(summary = "Webhook de ePayco (verifica firma SHA256 + procesa el evento)")
    @PostMapping("/epayco")
    public ApiResponse<Void> epayco(@RequestBody String body,
                                    @RequestHeader(value = "Content-Type", required = false) String contentType) {
        Map<String, String> params = parseWebhookParams(body, contentType);
        log.info("Webhook ePayco recibido: {}", params.keySet());

        PaymentGateway.PaymentEvent event = paymentGateway.handleWebhook(params);
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

    /**
     * Parsea los parámetros del webhook. ePayco puede enviar como
     * application/x-www-form-urlencoded o como JSON.
     */
    private Map<String, String> parseWebhookParams(String body, String contentType) {
        Map<String, String> params = new HashMap<>();

        if (contentType != null && contentType.contains("application/json")) {
            // JSON: parsear como mapa simple
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> json = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(body, Map.class);
                json.forEach((k, v) -> params.put(k, v != null ? v.toString() : null));
            } catch (Exception ex) {
                log.error("Error parseando webhook ePayco JSON", ex);
            }
        } else {
            // form-urlencoded: key=value&key=value
            for (String pair : body.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    String key = urlDecode(pair.substring(0, eq));
                    String value = urlDecode(pair.substring(eq + 1));
                    params.put(key, value);
                }
            }
        }
        return params;
    }

    private String urlDecode(String s) {
        try {
            return java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}