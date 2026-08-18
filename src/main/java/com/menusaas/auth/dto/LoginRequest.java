package com.menusaas.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "Correo inválido")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        String password
) {
}