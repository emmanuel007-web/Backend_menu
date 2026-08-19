package com.menusaas.admin.controller;

import com.menusaas.admin.dto.*;
import com.menusaas.admin.service.AdminService;
import com.menusaas.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin", description = "Gestión global del SaaS (solo SUPER_ADMIN)")
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @Operation(summary = "Métricas globales de la plataforma")
    @GetMapping("/stats")
    public ApiResponse<AdminStatsResponse> getStats() {
        return ApiResponse.ok(adminService.getStats());
    }

    @Operation(summary = "Listar todos los restaurantes registrados")
    @GetMapping("/restaurants")
    public ApiResponse<List<AdminRestaurantResponse>> listRestaurants() {
        return ApiResponse.ok(adminService.listRestaurants());
    }

    @Operation(summary = "Crear un nuevo restaurante y su usuario administrador")
    @PostMapping("/restaurants")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminRestaurantResponse> createRestaurant(@Valid @RequestBody AdminCreateRestaurantRequest request) {
        return ApiResponse.ok("Restaurante creado exitosamente", adminService.createRestaurant(request));
    }

    @Operation(summary = "Activar o desactivar restaurante por id")
    @PatchMapping("/restaurants/{id}/active")
    public ApiResponse<Void> toggleRestaurantActive(@PathVariable Long id, @RequestParam boolean active) {
        adminService.toggleRestaurantActive(id, active);
        return ApiResponse.ok(active ? "Restaurante activado" : "Restaurante desactivado");
    }

    @Operation(summary = "Listar todos los usuarios de la plataforma")
    @GetMapping("/users")
    public ApiResponse<List<AdminUserResponse>> listUsers() {
        return ApiResponse.ok(adminService.listUsers());
    }

    @Operation(summary = "Activar o desactivar un usuario de la plataforma")
    @PatchMapping("/users/{id}/active")
    public ApiResponse<Void> toggleUserActive(@PathVariable Long id, @RequestParam boolean active) {
        adminService.toggleUserActive(id, active);
        return ApiResponse.ok(active ? "Usuario activado" : "Usuario desactivado");
    }
}
