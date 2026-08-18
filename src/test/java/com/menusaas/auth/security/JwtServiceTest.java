package com.menusaas.auth.security;

import com.menusaas.config.AppProperties;
import com.menusaas.users.entity.Role;
import com.menusaas.users.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                new AppProperties.Jwt("dGVzdC1zZWNyZXQtbWVudS1zYWFzLWp3dC1zZWNyZXQtbG9uZy1lbm91Z2gtMjAyNi0wOA==", 15, 7),
                new AppProperties.Cors(List.of("http://localhost:4200")),
                "http://localhost:4200", "./uploads"
        );
        jwtService = new JwtService(props);
    }

    @Test
    void accessToken_containsUserAndTenantClaims() {
        User user = user(1L, 42L, "admin@x.com");

        Claims claims = jwtService.parse(jwtService.generateAccessToken(user));

        assertThat(claims.getSubject()).isEqualTo("admin@x.com");
        assertThat(claims.get("uid", Long.class)).isEqualTo(1L);
        assertThat(claims.get("rid", Long.class)).isEqualTo(42L);
        assertThat(claims.getExpiration()).isAfter(new java.util.Date());
    }

    @Test
    void tokenWithTamperedSignature_isRejected() {
        String token = jwtService.generateAccessToken(user(1L, 42L, "admin@x.com"));
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThatThrownBy(() -> jwtService.parse(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void malformedToken_isRejected() {
        assertThatThrownBy(() -> jwtService.parse("no-es-un-jwt"))
                .isInstanceOf(JwtException.class);
    }

    private User user(Long id, Long restaurantId, String email) {
        Role role = new Role(2L, Role.RESTAURANT_ADMIN, null);
        com.menusaas.restaurants.entity.Restaurant restaurant =
                com.menusaas.restaurants.entity.Restaurant.builder().id(restaurantId).build();
        return User.builder().id(id).email(email).role(role).restaurant(restaurant).build();
    }
}