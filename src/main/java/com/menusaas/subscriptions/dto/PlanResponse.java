package com.menusaas.subscriptions.dto;

import java.math.BigDecimal;

public record PlanResponse(
        Long id,
        String code,
        String name,
        String description,
        BigDecimal priceMonthly
) {
}