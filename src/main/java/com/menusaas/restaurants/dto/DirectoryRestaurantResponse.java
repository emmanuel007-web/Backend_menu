package com.menusaas.restaurants.dto;

/**
 * Restaurante resumido para el directorio publico (modulo Explore).
 * Solo datos que el restaurante publica; sin informacion sensible.
 */
public record DirectoryRestaurantResponse(
        Long id,
        String name,
        String slug,
        String description,
        String logoUrl,
        String phone,
        String whatsapp,
        String address,
        boolean open,
        long productCount
) {
}
