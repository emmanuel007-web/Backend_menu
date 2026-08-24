package com.menusaas.auth.service;

import com.menusaas.auth.dto.AuthResponse;
import com.menusaas.auth.dto.LoginRequest;
import com.menusaas.auth.dto.RegisterRequest;
import com.menusaas.auth.security.JwtService;
import com.menusaas.auth.security.RefreshToken;
import com.menusaas.auth.security.RefreshTokenRepository;
import com.menusaas.auth.security.UserPrincipal;
import com.menusaas.config.AppProperties;
import com.menusaas.restaurants.entity.Restaurant;
import com.menusaas.restaurants.repository.RestaurantRepository;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.shared.api.ConflictException;
import com.menusaas.shared.api.ForbiddenException;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.shared.security.SecurityUtils;
import com.menusaas.users.entity.Role;
import com.menusaas.users.entity.User;
import com.menusaas.users.repository.RoleRepository;
import com.menusaas.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
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
    private final AppProperties appProperties;

    /**
     * Resultado interno: los tokens se entregan al controlador para
     * escribirlos como cookies HttpOnly (nunca llegan al cuerpo JSON).
     */
    public record AuthResult(String accessToken, String refreshToken, AuthResponse.UserInfo user) {
    }

    @Transactional
    public AuthResult register(RegisterRequest request) {
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
        return buildAuthResult(user);
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizeEmail(request.email()), request.password()));

        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> new BadRequestException("Credenciales inválidas"));

        if (!user.isActive()) {
            throw new ForbiddenException("La cuenta está desactivada");
        }
        return buildAuthResult(user);
    }

    @Transactional
    public AuthResult refresh(String refreshToken) {
        String hashed = hashToken(refreshToken);
        RefreshToken stored = refreshTokenRepository.findByToken(hashed)
                .orElseThrow(() -> new BadRequestException("Sesión expirada, inicie sesión nuevamente"));

        // Detección de robo: un token revocado que vuelve a presentarse indica
        // que dos partes lo usan. Se mata la sesion completa del usuario.
        if (stored.isRevoked()) {
            int killed = refreshTokenRepository.revokeAllActiveByUserId(stored.getUserId());
            log.warn("REUSO DE REFRESH TOKEN detectado para userId={}: {} sesiones revocadas",
                    stored.getUserId(), killed);
            throw new ForbiddenException("Sesión inválida, inicie sesión nuevamente");
        }

        if (stored.getExpiresAt().isAfter(Instant.now())
                && stored.getSessionExpiresAt().isAfter(Instant.now())) {
            User user = userRepository.findById(stored.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

            if (!user.isActive()) {
                throw new ForbiddenException("La cuenta está desactivada");
            }

            // Rotación: el refresh token usado se revoca y se emite uno nuevo.
            // El tope absoluto de sesion se hereda SIN extender.
            stored.setRevoked(true);
            refreshTokenRepository.save(stored);

            return buildAuthResult(user, stored.getSessionExpiresAt());
        }

        throw new BadRequestException("Sesión expirada, inicie sesión nuevamente");
    }

    @Transactional(readOnly = true)
    public AuthResponse.UserInfo currentUserInfo() {
        Long userId = SecurityUtils.currentUser().getId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return new AuthResponse.UserInfo(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().getName(),
                user.getRestaurant() != null ? user.getRestaurant().getId() : null
        );
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

    private AuthResult buildAuthResult(User user) {
        Instant sessionExpiry = Instant.now()
                .plus(appProperties.security().sessionAbsoluteTtlHours(), ChronoUnit.HOURS);
        return buildAuthResult(user, sessionExpiry);
    }

    private AuthResult buildAuthResult(User user, Instant sessionExpiresAt) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                // En BD solo se guarda el hash: un dump de la tabla no permite
                // secuestrar sesiones.
                .token(hashToken(refreshToken))
                .expiresAt(jwtService.refreshTokenExpiry())
                .sessionExpiresAt(sessionExpiresAt)
                .revoked(false)
                .build());

        AuthResponse.UserInfo userInfo = new AuthResponse.UserInfo(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().getName(),
                user.getRestaurant() != null ? user.getRestaurant().getId() : null
        );
        return new AuthResult(accessToken, refreshToken, userInfo);
    }

    static String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
