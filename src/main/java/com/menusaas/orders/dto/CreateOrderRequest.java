package com.menusaas.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank(message = "El nombre del cliente es obligatorio")
        @Size(max = 120, message = "El nombre del cliente no puede superar 120 caracteres")
        String customerName,

        @Size(max = 30, message = "El teléfono no puede superar 30 caracteres")
        String customerPhone,

        @Size(max = 30, message = "El número de mesa o dirección no puede superar 30 caracteres")
        String tableNumber,

        String notes,

        @NotEmpty(message = "El pedido debe contener al menos un producto")
        @Valid
        List<OrderItemRequest> items
) {
}
