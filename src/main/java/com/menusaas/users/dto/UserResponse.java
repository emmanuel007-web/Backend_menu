package com.menusaas.users.dto;

import com.menusaas.users.entity.User;

import java.time.Instant;

public record UserResponse(
        Long id,
        String name,
        String email,
        String role,
        Long restaurantId,
        boolean active,
        Instant createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(), user.getName(), user.getEmail(),
                user.getRole().getName(),
                user.getRestaurant() != null ? user.getRestaurant().getId() : null,
                user.isActive(), user.getCreatedAt()
        );
    }
}