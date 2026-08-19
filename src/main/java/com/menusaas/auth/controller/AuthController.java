package com.menusaas.auth.controller;

import com.menusaas.auth.dto.AuthResponse;
import com.menusaas.auth.dto.LoginRequest;
import com.menusaas.auth.dto.RegisterRequest;
import com.menusaas.auth.security.CookieService;
import com.menusaas.auth.service.AuthService;
import com.menusaas.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "Registro, login, refresh y logout (tokens en cookies HttpOnly)")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CookieService cookieService;

    @Operation(summary = "Registrar un nuevo restaurante + usuario administrador")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                              HttpServletResponse response) {
        AuthService.AuthResult result = authService.register(request);
        writeCookies(response, result);
        return ApiResponse.ok("Registro exitoso", new AuthResponse(result.user()));
    }

    @Operation(summary = "Iniciar sesión")
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                           HttpServletResponse response) {
        AuthService.AuthResult result = authService.login(request);
        writeCookies(response, result);
        return ApiResponse.ok("Login exitoso", new AuthResponse(result.user()));
    }

    @Operation(summary = "Renovar tokens con la cookie refresh_token (rotación)")
    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        AuthService.AuthResult result = authService.refresh(cookieService.readRefreshToken(request));
        writeCookies(response, result);
        return ApiResponse.ok("Tokens renovados", new AuthResponse(result.user()));
    }

    @Operation(summary = "Cerrar sesión (revoca el refresh token y limpia cookies)")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(cookieService.readRefreshToken(request));
        cookieService.clearTokens(request, response);
        return ApiResponse.ok("Sesión cerrada");
    }

    @Operation(summary = "Revocar todos los refresh tokens del usuario autenticado")
    @PostMapping("/logout-all")
    public ApiResponse<Void> logoutAll() {
        authService.logoutAll(com.menusaas.shared.security.SecurityUtils.currentUser().getId());
        return ApiResponse.ok("Todas las sesiones cerradas");
    }

    @Operation(summary = "Perfil del usuario autenticado (restaurar sesión al recargar)")
    @GetMapping("/me")
    public ApiResponse<AuthResponse.UserInfo> me() {
        return ApiResponse.ok(authService.currentUserInfo());
    }

    @Operation(summary = "Token CSRF para el bootstrap del frontend (cookie XSRF-TOKEN)")
    @GetMapping("/csrf")
    public ApiResponse<String> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return ApiResponse.ok("OK", token != null ? token.getToken() : "");
    }

    private void writeCookies(HttpServletResponse response, AuthService.AuthResult result) {
        cookieService.setAccessToken(response, result.accessToken());
        cookieService.setRefreshToken(response, result.refreshToken());
    }
}
