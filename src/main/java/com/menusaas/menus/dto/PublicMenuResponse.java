package com.menusaas.menus.dto;

import java.math.BigDecimal;

public record PublicMenuResponse(
        RestaurantInfo restaurant,
        java.util.List<CategoryInfo> categories
) {

    public record RestaurantInfo(
            String name,
            String slug,
            String logoUrl,
            String description,
            String phone,
            String address,
            String whatsapp,
            String instagram,
            String facebook,
            boolean open
    ) {
    }

    public record CategoryInfo(
            Long id,
            String name,
            String description,
            int position,
            java.util.List<ProductInfo> products
    ) {
    }

    public record ProductInfo(
            Long id,
            String name,
            String description,
            BigDecimal price,
            String imageUrl,
            boolean available
    ) {
    }
}