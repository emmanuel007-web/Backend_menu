package com.menusaas.subscriptions.payment;

import com.menusaas.config.AppProperties;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.subscriptions.entity.Plan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

/**
 * Unit test de EpaycoPaymentGateway: login en Apify, creación de sesión de
 * checkout y webhooks (firma SHA256 + estados), sin llamadas reales a ePayco
 * (MockRestServiceServer sobre un RestClient inyectado).
 */
class EpaycoPaymentGatewayTest {

    private MockRestServiceServer server;
    private EpaycoPaymentGateway gateway;

    private final AppProperties props = new AppProperties(
            new AppProperties.Jwt("secret-largo-para-tests", 15, 7),
            new AppProperties.Cors(java.util.List.of("http://localhost:4200")),
            "http://localhost:4200", "http://localhost:8080", "./uploads",
            new AppProperties.Security(false, 3600),
            new AppProperties.Payments("pub_123", "priv_456", "1000", "pkey_abc"));

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new EpaycoPaymentGateway(props, builder.build());
    }

    private Plan plan() {
        return Plan.builder()
                .id(1L).code("PRO").name("Profesional")
                .description("Plan Pro").priceMonthly(new BigDecimal("29900")).active(true)
                .build();
    }

    @Test
    void isConfigured_reflectsPublicKey() {
        assertThat(gateway.isConfigured()).isTrue();

        AppProperties noKey = new AppProperties(
                new AppProperties.Jwt("secret-largo-para-tests", 15, 7),
                null, null, null, null,
                new AppProperties.Security(false, 3600),
                new AppProperties.Payments(null, null, null, null));
        assertThat(new EpaycoPaymentGateway(noKey).isConfigured()).isFalse();
    }

    @Test
    void createCheckout_loginAndSession_success() {
        server.expect(requestTo("https://apify.epayco.co/login"))
                .andExpect(header("Authorization", "Basic cHViXzEyMzpwcml2XzQ1Ng=="))
                .andRespond(withSuccess(
                        "{\"token\":\"jwt-token-123\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://apify.epayco.co/payment/session/create"))
                .andExpect(header("Authorization", "Bearer jwt-token-123"))
                .andRespond(withSuccess(
                        "{\"success\":true,\"data\":{\"sessionId\":\"ses_42\",\"token\":\"ses-token\"}}",
                        MediaType.APPLICATION_JSON));

        PaymentGateway.CheckoutSession result = gateway.createCheckout(
                42L, plan(), "https://x/webhook", "https://x/success");

        assertThat(result.sessionId()).isEqualTo("ses_42");
        assertThat(result.token()).isEqualTo("ses-token");
        server.verify();
    }

    @Test
    void createCheckout_missingCredentials_throws() {
        AppProperties noCreds = new AppProperties(
                new AppProperties.Jwt("secret-largo-para-tests", 15, 7),
                null, null, null, null,
                new AppProperties.Security(false, 3600),
                new AppProperties.Payments("pub_123", null, null, null));
        EpaycoPaymentGateway noCredsGateway = new EpaycoPaymentGateway(noCreds, RestClient.create());

        assertThatThrownBy(() -> noCredsGateway.createCheckout(42L, plan(), "u1", "u2"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Credenciales ePayco no configuradas");
    }

    @Test
    void createCheckout_loginFailure_throws() {
        server.expect(requestTo("https://apify.epayco.co/login"))
                .andRespond(withSuccess("{\"error\":\"bad\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.createCheckout(42L, plan(), "u1", "u2"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No se pudo autenticar con ePayco");
    }

    @Test
    void createCheckout_unsuccessfulSession_throws() {
        server.expect(requestTo("https://apify.epayco.co/login"))
                .andRespond(withSuccess("{\"token\":\"t\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://apify.epayco.co/payment/session/create"))
                .andRespond(withSuccess("{\"success\":false}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.createCheckout(42L, plan(), "u1", "u2"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No se pudo crear la sesión de pago");
    }

    @Test
    void createCheckout_missingData_throws() {
        server.expect(requestTo("https://apify.epayco.co/login"))
                .andRespond(withSuccess("{\"token\":\"t\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://apify.epayco.co/payment/session/create"))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.createCheckout(42L, plan(), "u1", "u2"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Respuesta inválida de ePayco");
    }

    @Test
    void createCheckout_httpError_throws() {
        server.expect(requestTo("https://apify.epayco.co/login"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> gateway.createCheckout(42L, plan(), "u1", "u2"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void handleWebhook_missingParams_throws() {
        assertThatThrownBy(() -> gateway.handleWebhook(Map.of()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("parámetros incompletos");
    }

    @Test
    void handleWebhook_invalidSignature_throws() {
        Map<String, String> params = webhookParams("Aceptada", "ref999", "wrong-sig");

        assertThatThrownBy(() -> gateway.handleWebhook(params))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Firma de webhook ePayco inválida");
    }

    @Test
    void handleWebhook_validSignature_accepted_paymentCompleted() {
        Map<String, String> params = webhookParams("Aceptada", "ref999", null);

        PaymentGateway.PaymentEvent event = gateway.handleWebhook(params);

        assertThat(event).isNotNull();
        assertThat(event.type()).isEqualTo(PaymentGateway.PaymentEvent.TYPE_CHECKOUT_COMPLETED);
        assertThat(event.providerReference()).isEqualTo("ref999");
        assertThat(event.restaurantId()).isEqualTo(42L);
        assertThat(event.planCode()).isEqualTo("PRO");
    }

    @Test
    void handleWebhook_rejectedOrPending_returnsNull() {
        assertThat(gateway.handleWebhook(webhookParams("Rechazada", "ref1", null))).isNull();
        assertThat(gateway.handleWebhook(webhookParams("Pendiente", "ref2", null))).isNull();
        assertThat(gateway.handleWebhook(webhookParams("OtroEstado", "ref3", null))).isNull();
    }

    @Test
    void handleWebhook_withoutSignatureConfig_skipsValidation() {
        AppProperties noSigConfig = new AppProperties(
                new AppProperties.Jwt("secret-largo-para-tests", 15, 7),
                null, null, null, null,
                new AppProperties.Security(false, 3600),
                new AppProperties.Payments("pub_123", "priv_456", null, null));
        EpaycoPaymentGateway noSigGateway = new EpaycoPaymentGateway(noSigConfig, RestClient.create());

        Map<String, String> params = Map.of(
                "x_response", "Aceptada", "ref_payco", "ref1",
                "x_extra1", "42", "x_extra2", "PRO");

        PaymentGateway.PaymentEvent event = noSigGateway.handleWebhook(params);

        assertThat(event).isNotNull();
        assertThat(event.restaurantId()).isEqualTo(42L);
    }

    @Test
    void handleWebhook_badExtraIds_parseToNull() {
        AppProperties noSigConfig = new AppProperties(
                new AppProperties.Jwt("secret-largo-para-tests", 15, 7),
                null, null, null, null,
                new AppProperties.Security(false, 3600),
                new AppProperties.Payments("pub_123", "priv_456", null, null));
        EpaycoPaymentGateway noSigGateway = new EpaycoPaymentGateway(noSigConfig, RestClient.create());

        PaymentGateway.PaymentEvent event = noSigGateway.handleWebhook(Map.of(
                "x_response", "Aceptada", "ref_payco", "ref1",
                "x_extra1", "abc", "x_extra2", "PRO"));

        assertThat(event.restaurantId()).isNull();
    }

    /** Firma SHA256(customer^pkey^ref^tx^amount^currency) según validateSignature. */
    private Map<String, String> webhookParams(String response, String refPayco, String signatureOverride) {
        String tx = "tx_001";
        String amount = "29900";
        String currency = "COP";
        String signature = signatureOverride != null ? signatureOverride
                : sha256Hex("1000^pkey_abc^" + refPayco + "^" + tx + "^" + amount + "^" + currency);
        return Map.of(
                "x_response", response,
                "ref_payco", refPayco,
                "x_transaction_id", tx,
                "x_amount", amount,
                "x_currency_code", currency,
                "x_signature", signature,
                "x_extra1", "42",
                "x_extra2", "PRO");
    }

    private String sha256Hex(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}