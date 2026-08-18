package com.menusaas.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String name,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "Correo inválido")
        @Size(max = 160, message = "El correo no puede superar 160 caracteres")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "La contraseña debe incluir mayúscula, minúscula y número"
        )
        String password,

        @NotBlank(message = "El nombre del restaurante es obligatorio")
        @Size(max = 120, message = "El nombre del restaurante no puede superar 120 caracteres")
        String restaurantName,

        @NotBlank(message = "El slug es obligatorio")
        @Pattern(
                regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "El slug solo puede contener minúsculas, números y guiones"
        )
        @Size(max = 120, message = "El slug no puede superar 120 caracteres")
        String slug
) {
}