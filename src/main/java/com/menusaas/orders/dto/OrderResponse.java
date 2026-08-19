package com.menusaas.orders.dto;

import com.menusaas.orders.entity.Order;
import com.menusaas.orders.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        Long restaurantId,
        String orderNumber,
        String customerName,
        String customerPhone,
        String tableNumber,
        String notes,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant updatedAt,
        List<OrderItemResponse> items
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getRestaurantId(),
                order.getOrderNumber(),
                order.getCustomerName(),
                order.getCustomerPhone(),
                order.getTableNumber(),
                order.getNotes(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getItems() != null
                        ? order.getItems().stream().map(OrderItemResponse::from).toList()
                        : List.of()
        );
    }
}
