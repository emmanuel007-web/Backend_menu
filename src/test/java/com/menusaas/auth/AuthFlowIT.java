package com.menusaas.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.menusaas.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthFlowIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void fullAuthFlow_register_login_refresh_logout() throws Exception {
        // 1. Registro de nuevo restaurante
        ResponseEntity<JsonNode> register = rest.postForEntity("/api/auth/register",
                json(Map.of(
                        "name", "Test User",
                        "email", "test@example.com",
                        "password", "StrongPass123",
                        "restaurantName", "Test Burger",
                        "slug", "test-burger"
                )), JsonNode.class);

        assertThat(register.getStatusCode().is2xxSuccessful()).isTrue();
        String access = register.getBody().get("data").get("accessToken").asText();
        String refresh = register.getBody().get("data").get("refreshToken").asText();
        assertThat(access).isNotBlank();
        assertThat(refresh).isNotBlank();

        // 2. Login con credenciales
        ResponseEntity<JsonNode> login = rest.postForEntity("/api/auth/login",
                json(Map.of("email", "test@example.com", "password", "StrongPass123")), JsonNode.class);
        assertThat(login.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(login.getBody().get("data").get("user").get("role").asText()).isEqualTo("RESTAURANT_ADMIN");
        assertThat(login.getBody().get("data").get("user").get("restaurantId").asLong()).isPositive();

        // 3. Acceso protegido con el token
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(access);
        ResponseEntity<JsonNode> me = rest.exchange("/api/restaurants/me", HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class);
        assertThat(me.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(me.getBody().get("data").get("slug").asText()).isEqualTo("test-burger");

        // 4. Refresh con rotación
        ResponseEntity<JsonNode> refreshed = rest.postForEntity("/api/auth/refresh",
                json(Map.of("refreshToken", refresh)), JsonNode.class);
        assertThat(refreshed.getStatusCode().is2xxSuccessful()).isTrue();
        String newRefresh = refreshed.getBody().get("data").get("refreshToken").asText();

        // El refresh anterior ya no debe funcionar (rotación)
        ResponseEntity<JsonNode> reuse = rest.postForEntity("/api/auth/refresh",
                json(Map.of("refreshToken", refresh)), JsonNode.class);
        assertThat(reuse.getStatusCode().is4xxClientError()).isTrue();

        // 5. Logout revoca el refresh vigente
        ResponseEntity<JsonNode> logout = rest.postForEntity("/api/auth/logout",
                json(Map.of("refreshToken", newRefresh)), JsonNode.class);
        assertThat(logout.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<JsonNode> afterLogout = rest.postForEntity("/api/auth/refresh",
                json(Map.of("refreshToken", newRefresh)), JsonNode.class);
        assertThat(afterLogout.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void duplicateEmailOrSlug_rejected() throws Exception {
        ResponseEntity<JsonNode> first = rest.postForEntity("/api/auth/register",
                json(Map.of(
                        "name", "Dup User", "email", "dup@example.com", "password", "StrongPass123",
                        "restaurantName", "Dup Burger", "slug", "dup-burger")), JsonNode.class);
        assertThat(first.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<JsonNode> dupEmail = rest.postForEntity("/api/auth/register",
                json(Map.of(
                        "name", "Dup User 2", "email", "dup@example.com", "password", "StrongPass123",
                        "restaurantName", "Other Burger", "slug", "other-burger")), JsonNode.class);
        assertThat(dupEmail.getStatusCode().value()).isEqualTo(409);

        ResponseEntity<JsonNode> dupSlug = rest.postForEntity("/api/auth/register",
                json(Map.of(
                        "name", "Dup User 3", "email", "dup3@example.com", "password", "StrongPass123",
                        "restaurantName", "Other Burger", "slug", "dup-burger")), JsonNode.class);
        assertThat(dupSlug.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void weakPassword_rejected() throws Exception {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/auth/register",
                json(Map.of(
                        "name", "Weak User", "email", "weak@example.com", "password", "123",
                        "restaurantName", "Weak Burger", "slug", "weak-burger")), JsonNode.class);
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    private HttpEntity<String> json(Map<String, String> body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
    }
}