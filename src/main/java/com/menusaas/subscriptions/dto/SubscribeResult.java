package com.menusaas.subscriptions.dto;

/**
 * Resultado de una solicitud de suscripción.
 * checkoutUrl != null → redirigir al cliente a la pasarela de pagos.
 * checkoutUrl == null → la suscripción ya quedó activa (modo manual).
 */
public record SubscribeResult(SubscriptionResponse subscription, String checkoutUrl) {
}