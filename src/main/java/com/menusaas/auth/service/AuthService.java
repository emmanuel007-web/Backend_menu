package com.menusaas.auth.service;

import com.menusaas.auth.dto.LoginRequest;
import com.menusaas.auth.dto.RefreshRequest;
import com.menusaas.auth.dto.RegisterRequest;
import com.menusaas.auth.dto.TokenResponse;
import com.menusaas.auth.security.JwtService;
import com.menusaas.auth.security.RefreshToken;
import com.menusaas.auth.security.RefreshTokenRepository;
import com.menusaas.auth.security.UserPrincipal;
import com.menusaas.restaurants.entity.Restaurant;
import com.menusaas.restaurants.repository.RestaurantRepository;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.shared.api.ConflictException;
import com.menusaas.shared.api.ForbiddenException;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.users.entity.Role;
import com.menusaas.users.entity.User;
import com.menusaas.users.repository.RoleRepository;
import com.menusaas.users.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RestaurantRepository restaurantRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Ya existe una cuenta con este correo");
        }
        if (restaurantRepository.existsBySlug(request.slug())) {
            throw new ConflictException("El slug '" + request.slug() + "' ya está en uso");
        }

        Role role = roleRepository.findByName(Role.RESTAURANT_ADMIN)
                .orElseThrow(() -> new IllegalStateException("Rol RESTAURANT_ADMIN no configurado"));

        Restaurant restaurant = Restaurant.builder()
                .name(request.restaurantName().trim())
                .slug(request.slug())
                .active(true)
                .build();
        restaurant = restaurantRepository.save(restaurant);

        User user = User.builder()
                .name(request.name().trim())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .role(role)
                .restaurant(restaurant)
                .active(true)
                .build();
        user = userRepository.save(user);

        log.info("Nuevo restaurante registrado: slug={}, usuario={}", restaurant.getSlug(), email);
        return buildTokenResponse(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizeEmail(request.email()), request.password()));

        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> new BadRequestException("Credenciales inválidas"));

        if (!user.isActive()) {
            throw new ForbiddenException("La cuenta está desactivada");
        }
        return buildTokenResponse(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        RefreshToken stored = refreshTokenRepository.findByToken(request.refreshToken())
                .filter(t -> !t.isRevoked())
                .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new BadRequestException("Refresh token inválido o expirado"));

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (!user.isActive()) {
            throw new ForbiddenException("La cuenta está desactivada");
        }

        // Rotación: el refresh token usado se revoca y se emite uno nuevo.
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return buildTokenResponse(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByToken(refreshToken)
                .ifPresent(stored -> {
                    stored.setRevoked(true);
                    refreshTokenRepository.save(stored);
                });
    }

    @Transactional
    public void logoutAll(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    private TokenResponse buildTokenResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .token(refreshToken)
                .expiresAt(jwtService.refreshTokenExpiry())
                .revoked(false)
                .build());

        return new TokenResponse(
                accessToken,
                refreshToken,
                jwtService.accessTokenTtlSeconds(),
                new TokenResponse.UserInfo(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getRole().getName(),
                        user.getRestaurant() != null ? user.getRestaurant().getId() : null
                )
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}