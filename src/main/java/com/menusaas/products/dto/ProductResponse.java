package com.menusaas.products.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        Long restaurantId,
        Long categoryId,
        String name,
        String description,
        BigDecimal price,
        String imageUrl,
        boolean available,
        int position,
        Instant createdAt,
        Instant updatedAt
) {
}