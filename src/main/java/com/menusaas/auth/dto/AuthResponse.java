package com.menusaas.auth.dto;

/**
 * Respuesta de autenticación. Los tokens NO viajan en el cuerpo:
 * se envían como cookies HttpOnly; el cliente solo recibe el perfil del usuario.
 */
public record AuthResponse(UserInfo user) {

    public record UserInfo(Long id, String name, String email, String role, Long restaurantId) {
    }
}
