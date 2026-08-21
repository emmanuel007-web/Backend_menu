package com.menusaas.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.menusaas.BaseIntegrationTest;
import com.menusaas.TestHttp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ciclo de vida completo de pedidos: creación desde el menú público (sin
 * autenticación), validaciones (producto inexistente / no disponible / slug
 * desconocido / items vacíos), gestión por el restaurante (listado por estado,
 * detalle, cambio de estado) y aislamiento entre tenants.
 *
 * El restaurante demo "fritomix" (restaurant 1, products 1-8) lo siembra Flyway.
 */
class OrdersIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void publicOrder_validations_andLifecycle() throws Exception {
        // Pedido público válido contra el restaurante demo
        ResponseEntity<JsonNode> created = postPublicOrder("fritomix", Map.of(
                "customerName", "Cliente Demo",
                "customerPhone", "3001234567",
                "tableNumber", "Mesa 4",
                "notes", "Sin cebolla",
                "items", List.of(
                        Map.of("productId", 1, "quantity", 2, "notes", "Salsa extra"),
                        Map.of("productId", 4, "quantity", 1)
                )
        ));
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        JsonNode data = created.getBody().get("data");
        assertThat(data.get("orderNumber").asText()).startsWith("FRIT-");
        assertThat(data.get("status").asText()).isEqualTo("PENDING");
        assertThat(data.get("totalAmount").asDouble()).isEqualTo(62000.0);
        assertThat(data.get("items")).hasSize(2);
        assertThat(data.get("items").get(0).get("productName").asText()).isEqualTo("Hamburguesa Especial");
        assertThat(data.get("items").get(0).get("subtotal").asDouble()).isEqualTo(36000.0);
        assertThat(data.get("customerName").asText()).isEqualTo("Cliente Demo");

        // Producto inexistente → 400
        ResponseEntity<JsonNode> badProduct = postPublicOrder("fritomix", Map.of(
                "customerName", "C",
                "items", List.of(Map.of("productId", 99999, "quantity", 1))
        ));
        assertThat(badProduct.getStatusCode().value()).isEqualTo(400);

        // Slug desconocido → 404
        ResponseEntity<JsonNode> badSlug = postPublicOrder("no-existe", Map.of(
                "customerName", "C",
                "items", List.of(Map.of("productId", 1, "quantity", 1))
        ));
        assertThat(badSlug.getStatusCode().value()).isEqualTo(404);

        // Items vacíos → 400 (validación)
        ResponseEntity<JsonNode> emptyItems = postPublicOrder("fritomix", Map.of(
                "customerName", "C",
                "items", List.of()
        ));
        assertThat(emptyItems.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void unavailableProduct_isRejected() throws Exception {
        TestHttp.Session session = TestHttp.register(rest, objectMapper,
                "Orders User", "orders-user@test.com", "orders-user");

        // Crear categoría y producto, luego marcarlo como NO disponible
        ResponseEntity<JsonNode> category = rest.exchange("/api/categories", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of("name", "Papas", "position", 1, "active", true), session),
                JsonNode.class);
        long categoryId = category.getBody().get("data").get("id").asLong();

        ResponseEntity<JsonNode> product = rest.exchange("/api/products", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of(
                        "categoryId", categoryId,
                        "name", "Papas Francesas",
                        "price", 8000,
                        "available", true,
                        "position", 1), session),
                JsonNode.class);
        long productId = product.getBody().get("data").get("id").asLong();

        ResponseEntity<JsonNode> unavailable = rest.exchange("/api/products/" + productId, HttpMethod.PUT,
                TestHttp.body(objectMapper, Map.of(
                        "categoryId", categoryId,
                        "name", "Papas Francesas",
                        "price", 8000,
                        "available", false), session),
                JsonNode.class);
        assertThat(unavailable.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<JsonNode> order = postPublicOrder("orders-user", Map.of(
                "customerName", "C",
                "items", List.of(Map.of("productId", productId, "quantity", 1))
        ));
        assertThat(order.getStatusCode().value()).isEqualTo(400);
        assertThat(order.getBody().toString()).contains("no se encuentra disponible");
    }

    @Test
    void tenantOrderManagement_isolationAndStatusTransitions() throws Exception {
        TestHttp.Session owner = TestHttp.register(rest, objectMapper,
                "Orders Owner", "orders-owner@test.com", "orders-owner");
        TestHttp.Session other = TestHttp.register(rest, objectMapper,
                "Orders Other", "orders-other@test.com", "orders-other");

        // Crear producto propio y recibir un pedido público sobre él
        ResponseEntity<JsonNode> category = rest.exchange("/api/categories", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of("name", "Bebidas", "position", 1, "active", true), owner),
                JsonNode.class);
        long categoryId = category.getBody().get("data").get("id").asLong();

        ResponseEntity<JsonNode> product = rest.exchange("/api/products", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of(
                        "categoryId", categoryId,
                        "name", "Limonada",
                        "price", 5000,
                        "available", true), owner),
                JsonNode.class);
        long productId = product.getBody().get("data").get("id").asLong();

        ResponseEntity<JsonNode> placed = postPublicOrder("orders-owner", Map.of(
                "customerName", "Cliente",
                "items", List.of(Map.of("productId", productId, "quantity", 3))
        ));
        assertThat(placed.getStatusCode().value()).isEqualTo(201);
        long orderId = placed.getBody().get("data").get("id").asLong();
        assertThat(placed.getBody().get("data").get("totalAmount").asDouble()).isEqualTo(15000.0);

        // Listado sin filtro y filtrado por estado
        ResponseEntity<JsonNode> list = rest.exchange("/api/orders", HttpMethod.GET, owner.get(), JsonNode.class);
        assertThat(list.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(list.getBody().get("data")).hasSize(1);

        ResponseEntity<JsonNode> filtered = rest.exchange("/api/orders?status=PENDING", HttpMethod.GET,
                owner.get(), JsonNode.class);
        assertThat(filtered.getBody().get("data")).hasSize(1);

        ResponseEntity<JsonNode> filteredNone = rest.exchange("/api/orders?status=DELIVERED", HttpMethod.GET,
                owner.get(), JsonNode.class);
        assertThat(filteredNone.getBody().get("data")).hasSize(0);

        // Detalle propio → 200; ajeno → 404; inexistente → 404
        ResponseEntity<JsonNode> mine = rest.exchange("/api/orders/" + orderId, HttpMethod.GET,
                owner.get(), JsonNode.class);
        assertThat(mine.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(mine.getBody().get("data").get("id").asLong()).isEqualTo(orderId);

        ResponseEntity<JsonNode> others = rest.exchange("/api/orders/" + orderId, HttpMethod.GET,
                other.get(), JsonNode.class);
        assertThat(others.getStatusCode().value()).isEqualTo(404);

        ResponseEntity<JsonNode> missing = rest.exchange("/api/orders/99999", HttpMethod.GET,
                owner.get(), JsonNode.class);
        assertThat(missing.getStatusCode().value()).isEqualTo(404);

        // Transiciones de estado: PENDING → DELIVERED; re-modificar entregado → 400
        ResponseEntity<JsonNode> delivered = rest.exchange("/api/orders/" + orderId + "/status", HttpMethod.PATCH,
                TestHttp.body(objectMapper, Map.of("status", "DELIVERED"), owner), JsonNode.class);
        assertThat(delivered.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(delivered.getBody().get("data").get("status").asText()).isEqualTo("DELIVERED");

        ResponseEntity<JsonNode> reDelivered = rest.exchange("/api/orders/" + orderId + "/status", HttpMethod.PATCH,
                TestHttp.body(objectMapper, Map.of("status", "PENDING"), owner), JsonNode.class);
        assertThat(reDelivered.getStatusCode().value()).isEqualTo(400);

        // Cambiar estado de pedido ajeno → 404
        ResponseEntity<JsonNode> statusOthers = rest.exchange("/api/orders/" + orderId + "/status", HttpMethod.PATCH,
                TestHttp.body(objectMapper, Map.of("status", "CANCELLED"), other), JsonNode.class);
        assertThat(statusOthers.getStatusCode().value()).isEqualTo(404);

        // Pedido cancelado ya no se puede modificar
        ResponseEntity<JsonNode> secondOrder = postPublicOrder("orders-owner", Map.of(
                "customerName", "Cliente 2",
                "items", List.of(Map.of("productId", productId, "quantity", 1))
        ));
        long secondId = secondOrder.getBody().get("data").get("id").asLong();

        ResponseEntity<JsonNode> cancelled = rest.exchange("/api/orders/" + secondId + "/status", HttpMethod.PATCH,
                TestHttp.body(objectMapper, Map.of("status", "CANCELLED"), owner), JsonNode.class);
        assertThat(cancelled.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<JsonNode> afterCancel = rest.exchange("/api/orders/" + secondId + "/status", HttpMethod.PATCH,
                TestHttp.body(objectMapper, Map.of("status", "PENDING"), owner), JsonNode.class);
        assertThat(afterCancel.getStatusCode().value()).isEqualTo(400);
    }

    private ResponseEntity<JsonNode> postPublicOrder(String slug, Map<String, Object> body) {
        HttpEntity<String> entity = new HttpEntity<>(json(body), jsonHeaders());
        return rest.postForEntity("/api/public/orders/" + slug, entity, JsonNode.class);
    }

    private org.springframework.http.HttpHeaders jsonHeaders() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }

    private String json(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
