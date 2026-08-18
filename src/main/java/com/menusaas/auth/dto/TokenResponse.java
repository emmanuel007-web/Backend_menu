package com.menusaas.auth.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        UserInfo user
) {

    public record UserInfo(Long id, String name, String email, String role, Long restaurantId) {
    }
}