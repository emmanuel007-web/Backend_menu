package com.menusaas.orders.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrderItemRequest(
        @NotNull(message = "El id del producto es obligatorio")
        Long productId,

        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad debe ser al menos 1")
        Integer quantity,

        @Size(max = 255, message = "Las notas del producto no pueden superar 255 caracteres")
        String notes
) {
}
