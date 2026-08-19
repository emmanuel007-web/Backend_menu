package com.menusaas.admin.dto;

import java.time.Instant;

public record AdminRestaurantResponse(
        Long id,
        String name,
        String slug,
        String logoUrl,
        String phone,
        String address,
        boolean active,
        Instant createdAt,
        long userCount,
        long productCount,
        String planName,
        String adminEmail
) {
}
