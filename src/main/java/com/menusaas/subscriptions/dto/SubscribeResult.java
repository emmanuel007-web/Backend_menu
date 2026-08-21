package com.menusaas.subscriptions.dto;

/**
 * Resultado de una solicitud de suscripción.
 * checkoutSessionId != null → abrir Smart Checkout en el frontend con ese sessionId.
 * checkoutSessionId == null → la suscripción ya quedó activa (modo manual).
 */
public record SubscribeResult(SubscriptionResponse subscription, String checkoutSessionId) {
}