package com.menusaas.orders.dto;

import com.menusaas.orders.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record OrderStatusRequest(
        @NotNull(message = "El estado del pedido es obligatorio")
        OrderStatus status
) {
}
