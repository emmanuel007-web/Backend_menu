package com.menusaas.admin.dto;

import java.time.Instant;

public record AdminUserResponse(
        Long id,
        String name,
        String email,
        String role,
        boolean active,
        Instant createdAt,
        Long restaurantId,
        String restaurantName
) {
}
