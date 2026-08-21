package com.menusaas.qr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.menusaas.BaseIntegrationTest;
import com.menusaas.TestHttp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Descarga del QR del menú del restaurante autenticado: PNG, PDF y URL pública.
 */
class QrControllerIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void qrEndpoints_forAuthenticatedRestaurant() throws Exception {
        TestHttp.Session session = TestHttp.register(rest, objectMapper,
                "QR User", "qr@test.com", "qr-test");

        ResponseEntity<byte[]> png = rest.exchange("/api/qr/png", HttpMethod.GET,
                session.get(), byte[].class);
        assertThat(png.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(png.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(png.getBody()).isNotEmpty();
        assertThat(png.getBody()).startsWith(new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        ResponseEntity<byte[]> pdf = rest.exchange("/api/qr/pdf", HttpMethod.GET,
                session.get(), byte[].class);
        assertThat(pdf.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(pdf.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(pdf.getBody()).isNotEmpty();
        assertThat(pdf.getBody()).startsWith(new byte[]{0x25, 'P', 'D', 'F'});

        ResponseEntity<JsonNode> url = rest.exchange("/api/qr/url", HttpMethod.GET,
                session.get(), JsonNode.class);
        assertThat(url.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(url.getBody().get("data").get("url").asText())
                .contains("/menu/").contains("qr-test");
    }
}