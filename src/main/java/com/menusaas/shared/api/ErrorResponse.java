package com.menusaas.shared.api;

import java.time.Instant;
import java.util.Map;

/**
 * Cuerpo uniforme de error: mensaje, código de error y detalles de validación.
 */
public record ErrorResponse(
        String message,
        String code,
        int status,
        Instant timestamp,
        Map<String, String> fieldErrors
) {

    public static ErrorResponse of(int status, String code, String message, Map<String, String> fieldErrors) {
        return new ErrorResponse(message, code, status, Instant.now(), fieldErrors);
    }
}