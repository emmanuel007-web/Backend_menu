package com.menusaas.categories.dto;

import java.time.Instant;

public record CategoryResponse(
        Long id,
        Long restaurantId,
        String name,
        String description,
        int position,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}