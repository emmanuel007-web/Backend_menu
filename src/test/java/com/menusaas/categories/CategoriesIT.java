package com.menusaas.categories;

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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CategoriesIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void categoryCrud_tenantScoped_andConflictPaths() throws Exception {
        TestHttp.Session session = TestHttp.register(rest, objectMapper,
                "Categories User", "cat@test.com", "cat-test");

        // Crear con nombre duplicado → 409
        ResponseEntity<JsonNode> created = create(session, "Entradas", 1, true);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        ResponseEntity<JsonNode> dup = create(session, "entradas", 2, false);
        assertThat(dup.getStatusCode().value()).isEqualTo(HttpStatus.CONFLICT.value());

        // Crear con posición y active por defecto
        ResponseEntity<JsonNode> second = create(session, "Principales", null, null);
        assertThat(second.getStatusCode().value()).isEqualTo(201);
        long firstId = created.getBody().get("data").get("id").asLong();
        long secondId = second.getBody().get("data").get("id").asLong();
        assertThat(second.getBody().get("data").get("position").asInt()).isEqualTo(2);
        assertThat(second.getBody().get("data").get("active").asBoolean()).isTrue();

        // Listado paginado y ordenado
        ResponseEntity<JsonNode> list = rest.exchange("/api/categories?page=0&size=1", HttpMethod.GET,
                session.get(), JsonNode.class);
        assertThat(list.getBody().get("data").get("totalElements").asInt()).isEqualTo(2);
        assertThat(list.getBody().get("data").get("content").get(0).get("name").asText()).isEqualTo("Entradas");

        // Obtener una inexistente → 404
        ResponseEntity<JsonNode> missing = rest.exchange("/api/categories/99999", HttpMethod.GET,
                session.get(), JsonNode.class);
        assertThat(missing.getStatusCode().value()).isEqualTo(404);

        // Actualizar (cambio de nombre + posición) y conflicto al renombrar contra otra
        ResponseEntity<JsonNode> updated = rest.exchange("/api/categories/" + secondId, HttpMethod.PUT,
                TestHttp.body(objectMapper, Map.of("name", "Platos", "position", 3, "active", false), session),
                JsonNode.class);
        assertThat(updated.getStatusCode().value()).isEqualTo(200);
        assertThat(updated.getBody().get("data").get("name").asText()).isEqualTo("Platos");
        assertThat(updated.getBody().get("data").get("position").asInt()).isEqualTo(3);
        assertThat(updated.getBody().get("data").get("active").asBoolean()).isFalse();

        ResponseEntity<JsonNode> renameConflict = rest.exchange("/api/categories/" + secondId, HttpMethod.PUT,
                TestHttp.body(objectMapper, Map.of("name", "Entradas"), session), JsonNode.class);
        assertThat(renameConflict.getStatusCode().value()).isEqualTo(HttpStatus.CONFLICT.value());

        // Actualizar sin cambios de nombre ni posición (solo nombre repetido consigo misma → 200)
        ResponseEntity<JsonNode> renameSelf = rest.exchange("/api/categories/" + firstId, HttpMethod.PUT,
                TestHttp.body(objectMapper, Map.of("name", "Entradas"), session), JsonNode.class);
        assertThat(renameSelf.getStatusCode().value()).isEqualTo(200);

        // Actualizar inexistente → 404
        ResponseEntity<JsonNode> updateMissing = rest.exchange("/api/categories/99999", HttpMethod.PUT,
                TestHttp.body(objectMapper, Map.of("name", "X"), session), JsonNode.class);
        assertThat(updateMissing.getStatusCode().value()).isEqualTo(404);

        // Eliminar y comprobar 404 posterior
        ResponseEntity<JsonNode> deleted = rest.exchange("/api/categories/" + secondId, HttpMethod.DELETE,
                new HttpEntity<>(session.headers()), JsonNode.class);
        assertThat(deleted.getStatusCode().value()).isEqualTo(200);
        ResponseEntity<JsonNode> after = rest.exchange("/api/categories/" + secondId, HttpMethod.GET,
                session.get(), JsonNode.class);
        assertThat(after.getStatusCode().value()).isEqualTo(404);

        // Aislamiento por tenant: otro restaurante no ve ni toca la categoría
        TestHttp.Session other = TestHttp.register(rest, objectMapper,
                "Categories User 2", "cat2@test.com", "cat-test2");
        ResponseEntity<JsonNode> foreign = rest.exchange("/api/categories/" + firstId, HttpMethod.GET,
                other.get(), JsonNode.class);
        assertThat(foreign.getStatusCode().value()).isEqualTo(404);
    }

    private ResponseEntity<JsonNode> create(TestHttp.Session session, String name, Integer position, Boolean active)
            throws Exception {
        java.util.HashMap<String, Object> map = new java.util.HashMap<>();
        map.put("name", name);
        map.put("position", position);
        map.put("active", active);
        return rest.exchange("/api/categories", HttpMethod.POST,
                TestHttp.body(objectMapper, map, session), JsonNode.class);
    }
}