package com.menusaas.shared.api;

/**
 * Violación de aislamiento multi-tenant o de permisos por rol.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}