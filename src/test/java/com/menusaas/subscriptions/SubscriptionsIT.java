package com.menusaas.subscriptions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.menusaas.BaseIntegrationTest;
import com.menusaas.TestHttp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ciclo de vida de la suscripción en modo manual (sin pasarela configurada):
 * listar planes, suscribirse, cambiar de plan, cancelar. Sin ePayco, la
 * suscripción queda activa de inmediato y sin URL de pago.
 */
class SubscriptionsIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void subscriptionLifecycle_manualMode() throws Exception {
        TestHttp.Session session = TestHttp.register(rest, objectMapper,
                "Subs User", "subs@test.com", "subs-test");

        // Planes disponibles
        ResponseEntity<JsonNode> plans = rest.exchange("/api/subscriptions/plans", HttpMethod.GET,
                session.get(), JsonNode.class);
        assertThat(plans.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(plans.getBody().get("data").toString()).contains("PRO").contains("FREE");

        // Sin suscripción todavía → 404
        ResponseEntity<JsonNode> none = rest.exchange("/api/subscriptions/me", HttpMethod.GET,
                session.get(), JsonNode.class);
        assertThat(none.getStatusCode().value()).isEqualTo(404);

        // Suscribirse al plan PRO (modo manual → activa al instante, sin checkoutUrl)
        ResponseEntity<JsonNode> subscribed = subscribe(session, "PRO");
        assertThat(subscribed.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(subscribed.getBody().get("data").get("subscription").get("status").asText())
                .isEqualTo("ACTIVE");
        assertThat(subscribed.getBody().get("data").get("subscription").get("provider").asText())
                .isEqualTo("MANUAL");
        assertThat(subscribed.getBody().get("data").get("checkoutSessionId").isNull()).isTrue();

        // Cambiar a FREE reemplaza la anterior (queda cancelada)
        ResponseEntity<JsonNode> changed = subscribe(session, "FREE");
        assertThat(changed.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(changed.getBody().get("data").get("subscription").get("plan").get("code").asText())
                .isEqualTo("FREE");

        ResponseEntity<JsonNode> mine = rest.exchange("/api/subscriptions/me", HttpMethod.GET,
                session.get(), JsonNode.class);
        assertThat(mine.getBody().get("data").get("plan").get("code").asText()).isEqualTo("FREE");

        // Plan inexistente → 404
        ResponseEntity<JsonNode> bad = subscribe(session, "NO_EXISTE");
        assertThat(bad.getStatusCode().value()).isEqualTo(404);

        // Cancelar la suscripción activa
        ResponseEntity<JsonNode> cancelled = rest.exchange("/api/subscriptions/cancel", HttpMethod.POST,
                new HttpEntity<>(session.headers()), JsonNode.class);
        assertThat(cancelled.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(cancelled.getBody().get("data").get("status").asText()).isEqualTo("CANCELLED");

        // Webhook sin pasarela configurada → rechazado (firma criptográfica, no sesión)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> webhook = rest.exchange("/api/webhooks/epayco", HttpMethod.POST,
                new HttpEntity<>("{\"x_response\":\"Aceptada\",\"ref_payco\":\"ref123\"}", headers), JsonNode.class);
        assertThat(webhook.getStatusCode().value()).isEqualTo(400);
    }

    private ResponseEntity<JsonNode> subscribe(TestHttp.Session session, String planCode) throws Exception {
        return rest.exchange("/api/subscriptions/subscribe", HttpMethod.POST,
                TestHttp.body(objectMapper, Map.of("planCode", planCode), session), JsonNode.class);
    }
}