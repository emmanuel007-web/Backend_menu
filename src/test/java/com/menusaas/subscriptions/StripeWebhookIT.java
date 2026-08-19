package com.menusaas.subscriptions;

import com.menusaas.BaseIntegrationTest;
import com.menusaas.subscriptions.entity.Subscription;
import com.menusaas.subscriptions.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Webhooks de Stripe con autenticación por FIRMA HMAC (como lo haría Stripe).
 * Verifica la firma, procesa checkout.session.completed (activa la suscripción)
 * y customer.subscription.deleted (la cancela). Una firma inválida se rechaza.
 */
class StripeWebhookIT extends BaseIntegrationTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret_para_firmas_hmac";

    @DynamicPropertySource
    static void stripeProperties(DynamicPropertyRegistry registry) {
        registry.add("app.payments.stripe-secret-key", () -> "sk_test_dummy");
        registry.add("app.payments.stripe-webhook-secret", () -> WEBHOOK_SECRET);
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @BeforeEach
    void setUp() {
        subscriptionRepository.deleteAll();
    }

    @Test
    void webhook_withInvalidSignature_isRejected() {
        String payload = "{\"type\":\"checkout.session.completed\",\"data\":{}}";

        ResponseEntity<String> response = postWebhook(payload, "t=1,v1=firma-invalida");

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void webhook_checkoutCompleted_activatesSubscription() {
        String payload = """
                {
                  "id": "evt_completed_1",
                  "api_version": "2026-07-29.dahlia",
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "cs_test_1",
                      "object": "checkout.session",
                      "payment_status": "paid",
                      "metadata": {"restaurantId": "1", "planCode": "PRO"}
                    }
                  }
                }""";

        ResponseEntity<String> response = postWebhook(payload, stripeSignature(payload));

        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("status=%s body=%s", response.getStatusCode(), response.getBody())
                .isTrue();
        List<Subscription> subscriptions = subscriptionRepository.findAll();
        assertThat(subscriptions).hasSize(1);
        assertThat(subscriptions.get(0).getStatus()).isEqualTo(Subscription.STATUS_ACTIVE);
        assertThat(subscriptions.get(0).getProvider()).isEqualTo(Subscription.PROVIDER_STRIPE);
    }

    @Test
    void webhook_subscriptionDeleted_cancelsExisting() {
        subscriptionRepository.save(Subscription.builder()
                .restaurantId(1L).planId(1L).status(Subscription.STATUS_ACTIVE)
                .provider(Subscription.PROVIDER_STRIPE).providerReference("sub_456")
                .startsAt(Instant.now()).build());

        String payload = """
                {
                  "id": "evt_deleted_1",
                  "api_version": "2026-07-29.dahlia",
                  "type": "customer.subscription.deleted",
                  "data": {
                    "object": {
                      "id": "sub_456",
                      "object": "subscription",
                      "metadata": {"restaurantId": "1"}
                    }
                  }
                }""";

        ResponseEntity<String> response = postWebhook(payload, stripeSignature(payload));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Subscription updated = subscriptionRepository.findByProviderReference("sub_456").orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(Subscription.STATUS_CANCELLED);
    }

    @Test
    void webhook_withValidSignature_unknownEvent_isIgnored() {
        String payload = "{\"id\":\"evt_ignored\",\"type\":\"invoice.paid\",\"data\":{}}";

        ResponseEntity<String> response = postWebhook(payload, stripeSignature(payload));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(subscriptionRepository.findAll()).isEmpty();
    }

    private ResponseEntity<String> postWebhook(String payload, String signature) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("Stripe-Signature", signature);
        return rest.postForEntity("/api/webhooks/stripe", new HttpEntity<>(payload, headers), String.class);
    }

    /** Firma HMAC con el formato real de Stripe: t=<epoch>,v1=<hmac-sha256(secret, t.payload)>. */
    private String stripeSignature(String payload) {
        long timestamp = Instant.now().getEpochSecond();
        String signedContent = timestamp + "." + payload;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String hex = HexFormat.of().formatHex(mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8)));
            return "t=" + timestamp + ",v1=" + hex;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}