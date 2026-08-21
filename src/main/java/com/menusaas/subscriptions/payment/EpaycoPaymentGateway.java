package com.menusaas.subscriptions.payment;

import com.menusaas.config.AppProperties;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.subscriptions.entity.Plan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;

/**
 * Pasarela ePayco Smart Checkout v2 (Apify API).
 * Se activa cuando existe EPAYCO_PUBLIC_KEY.
 *
 * Flujo:
 * 1. Login con Basic Auth (PUBLIC_KEY:PRIVATE_KEY) → JWT token.
 * 2. Crear sesión de checkout → sessionId + token.
 * 3. Frontend abre Smart Checkout con sessionId.
 * 4. Webhook de confirmación valida firma SHA256.
 */
@Slf4j
public class EpaycoPaymentGateway implements PaymentGateway {

    private static final String APIFY_BASE_URL = "https://apify.epayco.co";
    private static final String CHECKOUT_VERSION = "2";
    private static final String CURRENCY = "COP";

    private final AppProperties appProperties;
    private final RestClient restClient;

    public EpaycoPaymentGateway(AppProperties appProperties) {
        this(appProperties, RestClient.create());
    }

    /** Constructor de test: permite inyectar un RestClient simulado. */
    EpaycoPaymentGateway(AppProperties appProperties, RestClient restClient) {
        this.appProperties = appProperties;
        this.restClient = restClient;
    }

    @Override
    public boolean isConfigured() {
        String publicKey = appProperties.payments().epaycoPublicKey();
        return publicKey != null && !publicKey.isBlank();
    }

    @Override
    public CheckoutSession createCheckout(Long restaurantId, Plan plan,
                                          String confirmationUrl, String responseUrl) {
        String token = login();

        Map<String, Object> body = Map.of(
                "checkout_version", CHECKOUT_VERSION,
                "name", plan.getName(),
                "currency", CURRENCY,
                "amount", plan.getPriceMonthly().longValue(),
                "confirmation", confirmationUrl,
                "response", responseUrl,
                "description", "Suscripción plan " + plan.getName(),
                "lang", "ES",
                "extras", Map.of(
                        "extra1", String.valueOf(restaurantId),
                        "extra2", plan.getCode()
                )
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri(APIFY_BASE_URL + "/payment/session/create")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null || !(Boolean.TRUE.equals(response.get("success")))) {
            log.error("Error creando sesión ePayco: {}", response);
            throw new BadRequestException("No se pudo crear la sesión de pago");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        if (data == null) {
            throw new BadRequestException("Respuesta inválida de ePayco: sin data");
        }

        String sessionId = (String) data.get("sessionId");
        String sessionToken = (String) data.get("token");

        log.info("Sesión ePayco creada: sessionId={}, restaurante={}, plan={}",
                sessionId, restaurantId, plan.getCode());

        return new CheckoutSession(sessionId, sessionToken);
    }

    @Override
    public PaymentEvent handleWebhook(Map<String, String> params) {
        String xResponse = params.get("x_response");
        String refPayco = params.get("ref_payco");
        String xTransactionId = params.get("x_transaction_id");
        String xAmount = params.get("x_amount");
        String xCurrencyCode = params.get("x_currency_code");
        String xSignature = params.get("x_signature");
        String xIdInvoice = params.get("x_id_invoice");

        if (xResponse == null || refPayco == null) {
            throw new BadRequestException("Webhook ePayco con parámetros incompletos");
        }

        validateSignature(params);

        Long restaurantId = parseExtra(params.get("x_extra1"), "restaurantId");
        String planCode = params.get("x_extra2");

        log.info("Webhook ePayco recibido: ref={}, response={}, transaction={}",
                refPayco, xResponse, xTransactionId);

        return switch (xResponse) {
            case "Aceptada" -> new PaymentEvent(
                    PaymentEvent.TYPE_CHECKOUT_COMPLETED,
                    refPayco,
                    restaurantId,
                    planCode,
                    null
            );
            case "Rechazada", "Fallida" -> {
                log.warn("Pago ePayco rechazado/fallido: ref={}, response={}, motivo={}",
                        refPayco, xResponse, params.get("x_response_reason_text"));
                yield null;
            }
            case "Pendiente" -> {
                log.info("Pago ePayco pendiente: ref={}", refPayco);
                yield null;
            }
            default -> {
                log.info("Estado ePayco no manejado: {}", xResponse);
                yield null;
            }
        };
    }

    /**
     * Login en Apify API con Basic Auth (PUBLIC_KEY:PRIVATE_KEY).
     */
    private String login() {
        String publicKey = appProperties.payments().epaycoPublicKey();
        String privateKey = appProperties.payments().epaycoPrivateKey();

        if (publicKey == null || privateKey == null ||
                publicKey.isBlank() || privateKey.isBlank()) {
            throw new BadRequestException("Credenciales ePayco no configuradas (EPAYCO_PUBLIC_KEY / EPAYCO_PRIVATE_KEY)");
        }

        String credentials = publicKey + ":" + privateKey;
        String basicAuth = "Basic " + java.util.Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri(APIFY_BASE_URL + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", basicAuth)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("token") == null) {
            log.error("Error en login ePayco: {}", response);
            throw new BadRequestException("No se pudo autenticar con ePayco");
        }

        return (String) response.get("token");
    }

    /**
     * Valida la firma SHA256 del webhook.
     * Firma: SHA256(customer_id^p_key^ref_payco^transaction_id^amount^currency)
     */
    private void validateSignature(Map<String, String> params) {
        String customerId = appProperties.payments().epaycoCustomerId();
        String pKey = appProperties.payments().epaycoPKey();
        String refPayco = params.get("ref_payco");
        String transactionId = params.get("x_transaction_id");
        String amount = params.get("x_amount");
        String currencyCode = params.get("x_currency_code");
        String receivedSignature = params.get("x_signature");

        if (customerId == null || pKey == null ||
                customerId.isBlank() || pKey.isBlank()) {
            log.warn("EPAYCO_CUSTOMER_ID o EPAYCO_P_KEY no configurados, omitiendo validación de firma");
            return;
        }

        if (receivedSignature == null || receivedSignature.isBlank()) {
            throw new BadRequestException("Firma de webhook ePayco ausente");
        }

        String payload = customerId + "^" + pKey + "^" + refPayco + "^"
                + transactionId + "^" + amount + "^" + currencyCode;

        String expectedSignature = sha256Hex(payload);

        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                receivedSignature.getBytes(StandardCharsets.UTF_8))) {
            log.error("Firma ePayco inválida: esperada={}, recibida={}", expectedSignature, receivedSignature);
            throw new BadRequestException("Firma de webhook ePayco inválida");
        }
    }

    private String sha256Hex(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Error calculando SHA-256", ex);
        }
    }

    private Long parseExtra(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            log.warn("Campo extra '{}' no presente en webhook ePayco", fieldName);
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            log.warn("No se pudo parsear extra '{}' como Long: {}", fieldName, value);
            return null;
        }
    }
}
