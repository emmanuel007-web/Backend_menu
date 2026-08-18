package com.menusaas.subscriptions.dto;

import jakarta.validation.constraints.NotBlank;

public record SubscriptionRequest(
        @NotBlank(message = "El código del plan es obligatorio")
        String planCode
) {
}