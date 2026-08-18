package com.menusaas.shared.security;

import com.menusaas.auth.security.UserPrincipal;
import com.menusaas.shared.api.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Acceso tipado al usuario autenticado y a su tenant (restaurantId).
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static UserPrincipal currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new ForbiddenException("No autenticado");
        }
        return principal;
    }

    /**
     * Tenant derivado del JWT. Nunca de parámetros del cliente.
     */
    public static Long currentRestaurantId() {
        Long restaurantId = currentUser().getRestaurantId();
        if (restaurantId == null) {
            if (com.menusaas.users.entity.Role.SUPER_ADMIN.equals(currentUser().getRole())) {
                return 1L;
            }
            throw new ForbiddenException("El usuario no pertenece a un restaurante");
        }
        return restaurantId;
    }

    public static boolean hasRole(String role) {
        return currentUser().getRole().equals(role);
    }
}