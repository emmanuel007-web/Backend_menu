package com.menusaas.subscriptions.dto;

import java.time.Instant;

public record SubscriptionResponse(
        Long id,
        Long restaurantId,
        PlanResponse plan,
        String status,
        Instant startsAt,
        Instant endsAt
) {
}