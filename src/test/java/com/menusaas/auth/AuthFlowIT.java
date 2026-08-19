package com.menusaas.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.menusaas.BaseIntegrationTest;
import com.menusaas.TestHttp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthFlowIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void fullAuthFlow_register_login_refresh_logout() throws Exception {
        // 1. Bootstrap CSRF + registro de nuevo restaurante (tokens en cookies HttpOnly)
        TestHttp.Session session = TestHttp.register(rest, objectMapper,
                "Test User", "test@example.com", "test-burger");
        assertThat(session.accessToken()).isNotBlank();
        assertThat(session.refreshToken()).isNotBlank();
        assertThat(session.xsrfToken()).isNotBlank();

        // 2. Login con credenciales
        String xsrf = TestHttp.bootstrapCsrf(rest);
        TestHttp.Session logged = TestHttp.login(rest, objectMapper, "test@example.com", "StrongPass123!", xsrf);
        assertThat(logged.accessToken()).isNotBlank();

        // 3. Acceso protegido con la cookie (sin header Authorization)
        ResponseEntity<JsonNode> me = rest.exchange("/api/restaurants/me", HttpMethod.GET,
                logged.get(), JsonNode.class);
        assertThat(me.getStatusCode().is2xxSuccessful())
                .as("status=%s body=%s", me.getStatusCode(), me.getBody())
                .isTrue();
        assertThat(me.getBody().get("data").get("slug").asText()).isEqualTo("test-burger");

        // 4. /api/auth/me devuelve el perfil (para restaurar sesión al recargar)
        ResponseEntity<JsonNode> profile = rest.exchange("/api/auth/me", HttpMethod.GET,
                logged.get(), JsonNode.class);
        assertThat(profile.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(profile.getBody().get("data").get("email").asText()).isEqualTo("test@example.com");

        // 5. Refresh con rotación (usa la cookie refresh_token, sin cuerpo)
        ResponseEntity<JsonNode> refreshed = rest.exchange("/api/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(logged.headers()), JsonNode.class);
        assertThat(refreshed.getStatusCode().is2xxSuccessful()).isTrue();
        TestHttp.Session afterRefresh = TestHttp.sessionFrom(refreshed, xsrf);

        // El refresh anterior ya no debe funcionar (rotación)
        ResponseEntity<JsonNode> reuse = rest.exchange("/api/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(logged.headers()), JsonNode.class);
        assertThat(reuse.getStatusCode().is4xxClientError()).isTrue();

        // 6. Logout revoca el refresh vigente y limpia cookies
        ResponseEntity<JsonNode> logout = rest.exchange("/api/auth/logout", HttpMethod.POST,
                new HttpEntity<>(afterRefresh.headers()), JsonNode.class);
        assertThat(logout.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<JsonNode> afterLogout = rest.exchange("/api/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(afterRefresh.headers()), JsonNode.class);
        assertThat(afterLogout.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void mutatingRequests_withoutCsrfToken_areRejected() throws Exception {
        TestHttp.Session session = TestHttp.register(rest, objectMapper,
                "Csrf User", "csrf@example.com", "csrf-burger");

        // POST sin header X-XSRF-TOKEN → 403 (CSRF activo)
        ResponseEntity<JsonNode> rejected = rest.exchange("/api/categories", HttpMethod.POST,
                new HttpEntity<>(TestHttp.json(objectMapper, Map.of("name", "Cat X", "position", "1")),
                        sessionHeadersWithoutXsrf(session)), JsonNode.class);
        assertThat(rejected.getStatusCode().value()).isEqualTo(403);

        // Con el token CSRF → 201
        ResponseEntity<JsonNode> created = rest.exchange("/api/categories", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of("name", "Cat X", "position", "1"), session), JsonNode.class);
        assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void duplicateEmailOrSlug_rejected() throws Exception {
        TestHttp.register(rest, objectMapper, "Dup User", "dup@example.com", "dup-burger");

        ResponseEntity<JsonNode> dupEmail = rest.postForEntity("/api/auth/register",
                TestHttp.body(objectMapper, Map.of(
                        "name", "Dup User 2", "email", "dup@example.com", "password", "StrongPass123!",
                        "restaurantName", "Other Burger", "slug", "other-burger"),
                        new TestHttp.Session(null, null, TestHttp.bootstrapCsrf(rest))), JsonNode.class);
        assertThat(dupEmail.getStatusCode().value()).isEqualTo(409);

        ResponseEntity<JsonNode> dupSlug = rest.postForEntity("/api/auth/register",
                TestHttp.body(objectMapper, Map.of(
                        "name", "Dup User 3", "email", "dup3@example.com", "password", "StrongPass123!",
                        "restaurantName", "Other Burger", "slug", "dup-burger"),
                        new TestHttp.Session(null, null, TestHttp.bootstrapCsrf(rest))), JsonNode.class);
        assertThat(dupSlug.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void weakPassword_rejected() throws Exception {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/auth/register",
                TestHttp.body(objectMapper, Map.of(
                        "name", "Weak User", "email", "weak@example.com", "password", "123",
                        "restaurantName", "Weak Burger", "slug", "weak-burger"),
                        new TestHttp.Session(null, null, TestHttp.bootstrapCsrf(rest))), JsonNode.class);
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void passwordWithoutSymbol_rejected() throws Exception {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/auth/register",
                TestHttp.body(objectMapper, Map.of(
                        "name", "NoSym User", "email", "nosym@example.com", "password", "StrongPass123",
                        "restaurantName", "NoSym Burger", "slug", "nosym-burger"),
                        new TestHttp.Session(null, null, TestHttp.bootstrapCsrf(rest))), JsonNode.class);
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void negativeProductPrice_rejected() throws Exception {
        TestHttp.Session session = TestHttp.register(rest, objectMapper,
                "Price User", "price@example.com", "price-burger");

        ResponseEntity<JsonNode> created = rest.exchange("/api/categories", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of("name", "Cat P", "position", "1"), session), JsonNode.class);
        long categoryId = created.getBody().get("data").get("id").asLong();

        ResponseEntity<JsonNode> negative = rest.exchange("/api/products", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of("categoryId", String.valueOf(categoryId),
                        "name", "Prod", "price", "-5"), session), JsonNode.class);
        assertThat(negative.getStatusCode().is4xxClientError()).isTrue();
    }

    private org.springframework.http.HttpHeaders sessionHeadersWithoutXsrf(TestHttp.Session session) {
        org.springframework.http.HttpHeaders headers = session.headers();
        headers.remove(TestHttp.HEADER_XSRF);
        return headers;
    }
}