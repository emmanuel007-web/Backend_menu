package com.menusaas.admin;

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

/**
 * Panel de SUPER_ADMIN: métricas globales, CRUD de restaurantes con su usuario
 * administrador, activación/desactivación de restaurantes y usuarios, y
 * prohibición de acceso a roles no superiores. El super admin demo lo siembra
 * Flyway (superadmin@demo.com / SuperAdmin123!).
 */
class AdminOperationsIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void superAdmin_fullLifecycle() throws Exception {
        TestHttp.Session superAdmin = superAdminSession();

        // Métricas globales (el seed aporta 1 restaurante, 2 usuarios, 1 suscripción activa)
        ResponseEntity<JsonNode> stats = rest.exchange("/api/admin/stats", HttpMethod.GET,
                superAdmin.get(), JsonNode.class);
        assertThat(stats.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(stats.getBody().get("data").get("totalRestaurants").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(stats.getBody().get("data").get("activeSubscriptions").asLong()).isGreaterThanOrEqualTo(1);

        // Listado de restaurantes (incluye plan y email del admin)
        ResponseEntity<JsonNode> restaurants = rest.exchange("/api/admin/restaurants", HttpMethod.GET,
                superAdmin.get(), JsonNode.class);
        assertThat(restaurants.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(restaurants.getBody().get("data").toString()).contains("fritomix");

        // Crear restaurante con plan explícito → 201 y suscripción activa
        ResponseEntity<JsonNode> created = createRestaurant(superAdmin,
                "Plan Libre", "plan-libre", "plan-libre-admin@test.com", "PlanLobre123!", "FREE");
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        JsonNode createdData = created.getBody().get("data");
        long restaurantId = createdData.get("id").asLong();
        assertThat(createdData.get("slug").asText()).isEqualTo("plan-libre");
        assertThat(createdData.get("planName").asText()).isEqualTo("Gratis");
        assertThat(createdData.get("adminEmail").asText()).isEqualTo("plan-libre-admin@test.com");
        assertThat(createdData.get("userCount").asLong()).isEqualTo(1);

        // Crear con plan por defecto (sin planCode → PRO) → 201
        ResponseEntity<JsonNode> defaultPlan = createRestaurant(superAdmin,
                "Plan Pro", "plan-pro", "plan-pro-admin@test.com", "PlanProAdmin123!", null);
        assertThat(defaultPlan.getStatusCode().value()).isEqualTo(201);
        assertThat(defaultPlan.getBody().get("data").get("planName").asText()).isEqualTo("Profesional");

        // Crear con planCode inexistente → cae al primer plan disponible
        ResponseEntity<JsonNode> fallbackPlan = createRestaurant(superAdmin,
                "Plan Fallback", "plan-fallback", "plan-fallback-admin@test.com", "Fallback123!", "NO_EXISTE");
        assertThat(fallbackPlan.getStatusCode().value()).isEqualTo(201);
        assertThat(fallbackPlan.getBody().get("data").get("planName")).isNotNull();

        // Conflictos: email duplicado → 409; slug duplicado → 409
        ResponseEntity<JsonNode> dupEmail = createRestaurant(superAdmin,
                "Otro Nombre", "otro-slug", "plan-libre-admin@test.com", "PlanLobre123!", "PRO");
        assertThat(dupEmail.getStatusCode().value()).isEqualTo(409);

        ResponseEntity<JsonNode> dupSlug = createRestaurant(superAdmin,
                "Otro Nombre", "plan-libre", "otro-email@test.com", "PlanLobre123!", "PRO");
        assertThat(dupSlug.getStatusCode().value()).isEqualTo(409);

        // Desactivar el restaurante creado → 200; reactivar → 200; inexistente → 404
        ResponseEntity<JsonNode> deactivated = rest.exchange(
                "/api/admin/restaurants/" + restaurantId + "/active?active=false", HttpMethod.PATCH,
                superAdmin.get(), JsonNode.class);
        assertThat(deactivated.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<JsonNode> reactivated = rest.exchange(
                "/api/admin/restaurants/" + restaurantId + "/active?active=true", HttpMethod.PATCH,
                superAdmin.get(), JsonNode.class);
        assertThat(reactivated.getStatusCode().is2xxSuccessful()).isTrue();

        ResponseEntity<JsonNode> missingRestaurant = rest.exchange(
                "/api/admin/restaurants/99999/active?active=false", HttpMethod.PATCH,
                superAdmin.get(), JsonNode.class);
        assertThat(missingRestaurant.getStatusCode().value()).isEqualTo(404);

        // Listado de usuarios
        ResponseEntity<JsonNode> users = rest.exchange("/api/admin/users", HttpMethod.GET,
                superAdmin.get(), JsonNode.class);
        assertThat(users.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(users.getBody().get("data").toString()).contains("superadmin@demo.com");
        long superAdminId = findIdByEmail(users.getBody().get("data"), "superadmin@demo.com");
        long createdUserId = findIdByEmail(users.getBody().get("data"), "plan-libre-admin@test.com");

        // Desactivar a un usuario (el del restaurante creado) → 200
        ResponseEntity<JsonNode> userDeactivated = rest.exchange(
                "/api/admin/users/" + createdUserId + "/active?active=false", HttpMethod.PATCH,
                superAdmin.get(), JsonNode.class);
        assertThat(userDeactivated.getStatusCode().is2xxSuccessful()).isTrue();

        // Desactivarse a sí mismo → 403
        ResponseEntity<JsonNode> selfDeactivate = rest.exchange(
                "/api/admin/users/" + superAdminId + "/active?active=false", HttpMethod.PATCH,
                superAdmin.get(), JsonNode.class);
        assertThat(selfDeactivate.getStatusCode().value()).isEqualTo(403);

        // Usuario inexistente → 404
        ResponseEntity<JsonNode> missingUser = rest.exchange(
                "/api/admin/users/99999/active?active=false", HttpMethod.PATCH,
                superAdmin.get(), JsonNode.class);
        assertThat(missingUser.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void nonSuperAdmin_isForbidden() throws Exception {
        TestHttp.Session regular = TestHttp.register(rest, objectMapper,
                "Admin Regular", "admin-regular@test.com", "admin-regular");

        ResponseEntity<JsonNode> stats = rest.exchange("/api/admin/stats", HttpMethod.GET,
                regular.get(), JsonNode.class);
        assertThat(stats.getStatusCode().value()).isEqualTo(403);

        ResponseEntity<JsonNode> users = rest.exchange("/api/admin/users", HttpMethod.GET,
                regular.get(), JsonNode.class);
        assertThat(users.getStatusCode().value()).isEqualTo(403);
    }

    private TestHttp.Session superAdminSession() throws Exception {
        String xsrf = TestHttp.bootstrapCsrf(rest);
        return TestHttp.login(rest, objectMapper, "superadmin@demo.com", "SuperAdmin123!", xsrf);
    }

    private ResponseEntity<JsonNode> createRestaurant(TestHttp.Session session, String restaurantName,
                                                      String slug, String email, String password, String planCode)
            throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>(Map.of(
                "restaurantName", restaurantName,
                "slug", slug,
                "adminName", "Admin " + restaurantName,
                "adminEmail", email,
                "adminPassword", password
        ));
        if (planCode != null) {
            body.put("planCode", planCode);
        }
        return rest.exchange("/api/admin/restaurants", HttpMethod.POST,
                TestHttp.body(objectMapper, body, session), JsonNode.class);
    }

    private long findIdByEmail(JsonNode users, String email) {
        for (JsonNode user : users) {
            if (email.equals(user.get("email").asText())) {
                return user.get("id").asLong();
            }
        }
        throw new IllegalStateException("Usuario no encontrado: " + email);
    }
}
