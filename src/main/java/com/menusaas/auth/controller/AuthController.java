package com.menusaas.auth.controller;

import com.menusaas.auth.dto.LoginRequest;
import com.menusaas.auth.dto.RefreshRequest;
import com.menusaas.auth.dto.RegisterRequest;
import com.menusaas.auth.dto.TokenResponse;
import com.menusaas.auth.service.AuthService;
import com.menusaas.shared.api.ApiResponse;
import com.menusaas.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "Registro, login, refresh y logout")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Registrar un nuevo restaurante + usuario administrador")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok("Registro exitoso", authService.register(request));
    }

    @Operation(summary = "Iniciar sesión")
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok("Login exitoso", authService.login(request));
    }

    @Operation(summary = "Renovar tokens con refresh token (rotación)")
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok("Tokens renovados", authService.refresh(request));
    }

    @Operation(summary = "Cerrar sesión (revoca el refresh token)")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) RefreshRequest request) {
        authService.logout(request != null ? request.refreshToken() : null);
        return ApiResponse.ok("Sesión cerrada");
    }

    @Operation(summary = "Revocar todos los refresh tokens del usuario autenticado")
    @PostMapping("/logout-all")
    public ApiResponse<Void> logoutAll() {
        authService.logoutAll(SecurityUtils.currentUser().getId());
        return ApiResponse.ok("Todas las sesiones cerradas");
    }
}