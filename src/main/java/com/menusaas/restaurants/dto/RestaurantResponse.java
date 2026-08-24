package com.menusaas.restaurants.dto;

import java.time.Instant;

public record RestaurantResponse(
        Long id,
        String name,
        String slug,
        String logoUrl,
        String description,
        String phone,
        String address,
        String whatsapp,
        String instagram,
        String facebook,
        boolean active,
        boolean open,
        Instant createdAt,
        Instant updatedAt
) {
}