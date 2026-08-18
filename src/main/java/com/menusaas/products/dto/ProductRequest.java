package com.menusaas.products.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @NotNull(message = "La categoría es obligatoria")
        Long categoryId,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 160, message = "El nombre no puede superar 160 caracteres")
        String name,

        @Size(max = 2000, message = "La descripción no puede superar 2000 caracteres")
        String description,

        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
        BigDecimal price,

        @Size(max = 500, message = "La URL de la imagen no puede superar 500 caracteres")
        String imageUrl,

        Boolean available,

        Integer position
) {
}