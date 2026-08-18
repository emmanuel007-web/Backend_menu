package com.menusaas.tenancy;

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

/**
 * La regla de oro del SaaS: un restaurante JAMÁS ve datos de otro.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TenantIsolationIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void restaurantA_cannotReadOrMutate_restaurantB_data() throws Exception {
        // Dos restaurantes registrados
        String tokenA = registerAndGetToken("resta@example.com", "resta");
        String tokenB = registerAndGetToken("restb@example.com", "restb");

        // A crea una categoría
        ResponseEntity<JsonNode> createCatA = rest.exchange("/api/categories", HttpMethod.POST,
                auth(json(Map.of("name", "Cat A", "position", "1")), tokenA), JsonNode.class);
        assertThat(createCatA.getStatusCode().is2xxSuccessful()).isTrue();
        long catAId = createCatA.getBody().get("data").get("id").asLong();

        // B intenta obtener la categoría de A → 404
        ResponseEntity<JsonNode> stealCat = rest.exchange("/api/categories/" + catAId, HttpMethod.GET,
                auth(null, tokenB), JsonNode.class);
        assertThat(stealCat.getStatusCode().value()).isEqualTo(404);

        // A crea un producto en su categoría
        ResponseEntity<JsonNode> createProdA = rest.exchange("/api/products", HttpMethod.POST,
                auth(json(Map.of("categoryId", String.valueOf(catAId), "name", "Producto A", "price", "1000")), tokenA),
                JsonNode.class);
        assertThat(createProdA.getStatusCode().is2xxSuccessful()).isTrue();
        long prodAId = createProdA.getBody().get("data").get("id").asLong();

        // B intenta actualizar el producto de A → 404
        ResponseEntity<JsonNode> stealProd = rest.exchange("/api/products/" + prodAId, HttpMethod.PUT,
                auth(json(Map.of("categoryId", String.valueOf(catAId), "name", "Robado", "price", "1")), tokenB),
                JsonNode.class);
        assertThat(stealProd.getStatusCode().value()).isEqualTo(404);

        // B lista sus categorías: no ve ninguna de A
        ResponseEntity<JsonNode> listB = rest.exchange("/api/categories", HttpMethod.GET,
                auth(null, tokenB), JsonNode.class);
        assertThat(listB.getBody().get("data")).isEmpty();

        // A no puede usar una categoría de otro restaurante en un producto
        String tokenB2 = registerAndGetToken("restc@example.com", "restc");
        ResponseEntity<JsonNode> crossTenant = rest.exchange("/api/products", HttpMethod.POST,
                auth(json(Map.of("categoryId", String.valueOf(catAId), "name", "X", "price", "1")), tokenB2),
                JsonNode.class);
        assertThat(crossTenant.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void publicMenu_exposesOnlyActiveRestaurant_andReturns404ForUnknownSlug() throws Exception {
        registerAndGetToken("publica@example.com", "publica");

        ResponseEntity<JsonNode> menu = rest.getForEntity("/api/public/menu/publica", JsonNode.class);
        assertThat(menu.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(menu.getBody().get("data").get("restaurant").get("name").asText()).isNotBlank();

        ResponseEntity<JsonNode> unknown = rest.getForEntity("/api/public/menu/no-existe", JsonNode.class);
        assertThat(unknown.getStatusCode().value()).isEqualTo(404);

        // Sin token NO se puede acceder a rutas privadas
        ResponseEntity<JsonNode> protectedCall = rest.exchange("/api/categories", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), JsonNode.class);
        assertThat(protectedCall.getStatusCode().value()).isEqualTo(401);
    }

    private String registerAndGetToken(String email, String slug) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> register = rest.postForEntity("/api/auth/register",
                new HttpEntity<>(json(Map.of(
                        "name", "User " + slug,
                        "email", email,
                        "password", "StrongPass123",
                        "restaurantName", "Rest " + slug,
                        "slug", slug
                )), headers), JsonNode.class);
        assertThat(register.getStatusCode().is2xxSuccessful()).isTrue();
        return register.getBody().get("data").get("accessToken").asText();
    }

    private HttpEntity<String> auth(String body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
            return new HttpEntity<>(body, headers);
        }
        return new HttpEntity<>(headers);
    }

    private String json(Map<String, String> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}