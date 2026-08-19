package com.menusaas.files.security;

import com.menusaas.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Genera y valida URLs firmadas para servir imágenes sin autenticación.
 * La firma es HMAC-SHA256(fileId + ":" + expiración) con el secreto JWT,
 * de modo que solo el backend puede emitir URLs válidas con expiración.
 */
@Component
public class SignedUrlService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] keyBytes;
    private final String apiBaseUrl;
    private final long ttlSeconds;

    public SignedUrlService(AppProperties appProperties) {
        this.keyBytes = appProperties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        this.apiBaseUrl = stripTrailingSlash(appProperties.apiBaseUrl());
        this.ttlSeconds = appProperties.security().signedUrlTtlSeconds();
    }

    /**
     * Construye una URL firmada con expiración para un fileId almacenado.
     */
    public String buildSignedUrl(String fileId) {
        long expiresAt = Instant.now().plusSeconds(ttlSeconds).getEpochSecond();
        String signature = sign(fileId, expiresAt);
        return apiBaseUrl + "/api/public/files/" + fileId + "?exp=" + expiresAt + "&sig=" + signature;
    }

    /**
     * Convierte el valor almacenado en BD a una URL firmada (o lo devuelve tal cual
     * si es una URL externa legítima cargada por el cliente).
     */
    public String toSignedUrlOrNull(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        String trimmed = stored.trim();
        if (trimmed.contains("://") || trimmed.startsWith("/")) {
            return trimmed;
        }
        // Nombre de archivo simple (fileId) → URL firmada con expiración.
        if (trimmed.matches("[A-Za-z0-9._-]+")) {
            return buildSignedUrl(trimmed);
        }
        return trimmed;
    }

    /**
     * Valida firma y expiración. Comparación en tiempo constante (evita timing attacks).
     */
    public boolean isValid(String fileId, long expiresAt, String signature) {
        long now = Instant.now().getEpochSecond();
        if (expiresAt < now || expiresAt > now + ttlSeconds * 2) {
            return false;
        }
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String expected = sign(fileId, expiresAt);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String fileId, long expiresAt) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(keyBytes, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal((fileId + ":" + expiresAt).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo firmar la URL", ex);
        }
    }

    private String stripTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}