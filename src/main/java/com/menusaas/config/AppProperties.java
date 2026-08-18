package com.menusaas.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Cors cors,
        String appBaseUrl,
        String uploadDir
) {

    public AppProperties {
        if (appBaseUrl == null) appBaseUrl = "http://localhost:4200";
        if (uploadDir == null) uploadDir = "./uploads";
        if (cors == null) cors = new Cors(new ArrayList<>());
    }

    public record Jwt(
            @NotBlank String secret,
            @Min(1) @Max(1440) int accessTokenTtlMinutes,
            @Min(1) @Max(90) int refreshTokenTtlDays
    ) {

        public Jwt {
            if (secret == null || secret.isBlank()) {
                throw new IllegalArgumentException("app.jwt.secret no puede estar vacío");
            }
            if (accessTokenTtlMinutes == 0) accessTokenTtlMinutes = 15;
            if (refreshTokenTtlDays == 0) refreshTokenTtlDays = 7;
        }
    }

    public record Cors(List<String> allowedOrigins) {
        public List<String> allowedOrigins() {
            return allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        }
    }
}