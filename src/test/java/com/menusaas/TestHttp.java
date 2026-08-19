package com.menusaas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Helper de tests: autenticación vía cookies HttpOnly (como el frontend real).
 * - bootstrap: GET /api/auth/csrf para establecer la cookie XSRF-TOKEN.
 * - register/login: extrae access_token/refresh_token de las Set-Cookie.
 * - auth: arma Cookie + X-XSRF-TOKEN para peticiones autenticadas.
 */
public final class TestHttp {

    public static final String COOKIE_ACCESS = "access_token";
    public static final String COOKIE_REFRESH = "refresh_token";
    public static final String COOKIE_XSRF = "XSRF-TOKEN";
    public static final String HEADER_XSRF = "X-XSRF-TOKEN";

    private TestHttp() {
    }

    public record Session(String accessToken, String refreshToken, String xsrfToken) {
        public HttpHeaders headers() {
            HttpHeaders headers = new HttpHeaders();
            if (xsrfToken != null) {
                headers.add(HEADER_XSRF, xsrfToken);
            }
            String cookie = "";
            if (accessToken != null) cookie += COOKIE_ACCESS + "=" + accessToken + "; ";
            if (refreshToken != null) cookie += COOKIE_REFRESH + "=" + refreshToken + "; ";
            if (xsrfToken != null) cookie += COOKIE_XSRF + "=" + xsrfToken + "; ";
            if (!cookie.isBlank()) {
                headers.add(HttpHeaders.COOKIE, cookie);
            }
            return headers;
        }

        public HttpEntity<String> get() {
            return new HttpEntity<>(headers());
        }
    }

    /** Establece la cookie CSRF (bootstrap que hace el frontend). */
    public static String bootstrapCsrf(TestRestTemplate rest) {
        ResponseEntity<JsonNode> response = rest.getForEntity("/api/auth/csrf", JsonNode.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        String xsrf = extractCookie(response, COOKIE_XSRF);
        assertThat(xsrf).isNotBlank();
        return xsrf;
    }

    public static Session register(TestRestTemplate rest, ObjectMapper objectMapper,
                                   String name, String email, String slug) throws Exception {
        String xsrf = bootstrapCsrf(rest);
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/auth/register",
                body(objectMapper, Map.of(
                        "name", name,
                        "email", email,
                        "password", "StrongPass123!",
                        "restaurantName", "Rest " + name,
                        "slug", slug
                ), new Session(null, null, xsrf)), JsonNode.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return sessionFrom(response, xsrf);
    }

    public static Session login(TestRestTemplate rest, ObjectMapper objectMapper,
                                String email, String password, String xsrf) throws Exception {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/auth/login",
                body(objectMapper, Map.of("email", email, "password", password),
                        new Session(null, null, xsrf)), JsonNode.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return sessionFrom(response, xsrf);
    }

    public static Session sessionFrom(ResponseEntity<JsonNode> response, String xsrf) {
        return new Session(
                extractCookie(response, COOKIE_ACCESS),
                extractCookie(response, COOKIE_REFRESH),
                xsrf
        );
    }

    public static HttpEntity<String> body(ObjectMapper objectMapper, Map<String, String> body,
                                          Session session) throws Exception {
        return body(objectMapper, (Object) body, session);
    }

    public static HttpEntity<String> body(ObjectMapper objectMapper, Object body,
                                          Session session) throws Exception {
        HttpHeaders headers = session.headers();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
    }

    public static String extractCookie(ResponseEntity<?> response, String name) {
        if (response.getHeaders().get(HttpHeaders.SET_COOKIE) == null) {
            return null;
        }
        for (String setCookie : response.getHeaders().get(HttpHeaders.SET_COOKIE)) {
            String part = setCookie.split(";")[0].trim();
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(name)) {
                return part.substring(eq + 1);
            }
        }
        return null;
    }

    public static String json(ObjectMapper objectMapper, Map<String, String> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}