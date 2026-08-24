package com.menusaas.restaurants.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RestaurantRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String name,

        @NotBlank(message = "El slug es obligatorio")
        @Pattern(
                regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "El slug solo puede contener minúsculas, números y guiones"
        )
        @Size(max = 120, message = "El slug no puede superar 120 caracteres")
        String slug,

        @Size(max = 500, message = "La URL del logo no puede superar 500 caracteres")
        String logoUrl,

        @Size(max = 2000, message = "La descripción no puede superar 2000 caracteres")
        String description,

        @Size(max = 30, message = "El teléfono no puede superar 30 caracteres")
        String phone,

        @Size(max = 255, message = "La dirección no puede superar 255 caracteres")
        String address,

        @Size(max = 30, message = "El WhatsApp no puede superar 30 caracteres")
        String whatsapp,

        @Size(max = 120, message = "El Instagram no puede superar 120 caracteres")
        String instagram,

        @Size(max = 120, message = "El Facebook no puede superar 120 caracteres")
        String facebook,

        Boolean active,

        Boolean open
) {
}