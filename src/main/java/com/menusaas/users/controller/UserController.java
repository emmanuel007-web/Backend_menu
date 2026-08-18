package com.menusaas.users.controller;

import com.menusaas.shared.api.ApiResponse;
import com.menusaas.users.dto.CreateUserRequest;
import com.menusaas.users.dto.UserResponse;
import com.menusaas.users.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Users", description = "Usuarios del restaurante (tenant-scoped)")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Listar usuarios de mi restaurante")
    @GetMapping
    public ApiResponse<List<UserResponse>> list() {
        return ApiResponse.ok(userService.listMine());
    }

    @Operation(summary = "Obtener un usuario de mi restaurante")
    @GetMapping("/{id}")
    public ApiResponse<UserResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(userService.getMine(id));
    }

    @Operation(summary = "Crear usuario (solo RESTAURANT_ADMIN)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok("Usuario creado", userService.createMine(request));
    }

    @Operation(summary = "Activar/desactivar usuario (solo RESTAURANT_ADMIN)")
    @PatchMapping("/{id}/active")
    public ApiResponse<Void> setActive(@PathVariable Long id, @RequestParam boolean active) {
        userService.toggleActiveMine(id, active);
        return ApiResponse.ok(active ? "Usuario activado" : "Usuario desactivado");
    }

    @Operation(summary = "Eliminar usuario (solo RESTAURANT_ADMIN)")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.deleteMine(id);
        return ApiResponse.ok("Usuario eliminado");
    }
}