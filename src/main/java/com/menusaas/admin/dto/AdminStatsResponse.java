package com.menusaas.admin.dto;

public record AdminStatsResponse(
        long totalRestaurants,
        long activeRestaurants,
        long totalUsers,
        long activeSubscriptions,
        long totalProducts
) {
}
