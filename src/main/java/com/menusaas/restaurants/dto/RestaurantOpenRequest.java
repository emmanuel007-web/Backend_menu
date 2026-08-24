package com.menusaas.restaurants.dto;

import jakarta.validation.constraints.NotNull;

public record RestaurantOpenRequest(
        @NotNull(message = "El estado abierto/cerrado es obligatorio")
        Boolean open
) {
}
