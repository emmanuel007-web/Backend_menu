package com.menusaas.files;

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
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.MultiValueMap;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seguridad de archivos extremo a extremo: subir imagen → URL firmada →
 * acceso solo con firma válida y vigente. Sin firma no se puede acceder,
 * aunque se conozca el fileId.
 */
class SecurityFilesIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @org.springframework.boot.test.web.server.LocalServerPort
    int port;

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
            0x49, 0x48, 0x44, 0x52
    };

    /** Las URLs firmadas usan la api-base-url de configuración (8080 en tests); aquí apuntan al servidor real. */
    private String localUrl(String signedUrl) {
        return signedUrl.replace("http://localhost:8080", "http://localhost:" + port);
    }

    @Test
    void uploadAndSignedAccess_fullFlow() throws Exception {
        TestHttp.Session session = TestHttp.register(rest, objectMapper,
                "Files User", "files@test.com", "files-test");

        ResponseEntity<JsonNode> upload = uploadPng(session);
        assertThat(upload.getStatusCode().value()).isEqualTo(201);
        String fileId = upload.getBody().get("data").get("fileId").asText();
        assertThat(fileId).matches("[0-9a-f-]{36}\\.png");
        String url = upload.getBody().get("data").get("url").asText();
        assertThat(url).contains("/api/public/files/" + fileId).contains("exp=").contains("sig=");

        // La URL firmada sirve la imagen con el Content-Type detectado por magic bytes.
        ResponseEntity<byte[]> served = rest.getForEntity(localUrl(url), byte[].class);
        assertThat(served.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(served.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat(served.getBody()).isEqualTo(PNG_BYTES);
    }

    @Test
    void signedUrl_withTamperedSignature_isRejected() throws Exception {
        TestHttp.Session session = TestHttp.register(rest, objectMapper,
                "Files User 2", "files2@test.com", "files-test2");
        String url = uploadPng(session).getBody().get("data").get("url").asText();
        String tampered = url.replaceFirst("sig=[0-9a-f]+", "sig=" + "0".repeat(64));

        ResponseEntity<String> response = rest.getForEntity(localUrl(tampered), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void signedUrl_withExpiredSignature_isRejected() throws Exception {
        TestHttp.Session session = TestHttp.register(rest, objectMapper,
                "Files User 3", "files3@test.com", "files-test3");
        String url = uploadPng(session).getBody().get("data").get("url").asText();
        long past = Instant.now().minusSeconds(60).getEpochSecond();
        String expired = url.replaceFirst("exp=\\d+", "exp=" + past);

        ResponseEntity<String> response = rest.getForEntity(localUrl(expired), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void publicFile_withPathTraversalFileId_isRejected() {
        ResponseEntity<String> response = rest.getForEntity(
                "/api/public/files/../secret?exp=9999999999&sig=abc", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void upload_withDisallowedDeclaredType_isRejected() throws Exception {
        TestHttp.Session session = TestHttp.register(rest, objectMapper,
                "Files User 4", "files4@test.com", "files-test4");

        ResponseEntity<JsonNode> response = rest.exchange("/api/files/upload", HttpMethod.POST,
                new HttpEntity<>(multipart("logo.html", MediaType.TEXT_HTML), session.headers()), JsonNode.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void upload_withoutAuth_isRejected() {
        ResponseEntity<String> response = rest.postForEntity("/api/files/upload",
                new HttpEntity<>(multipart("logo.png", MediaType.IMAGE_PNG)), String.class);

        // Sin sesión: el filtro CSRF rechaza antes que la autenticación (403).
        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    private ResponseEntity<JsonNode> uploadPng(TestHttp.Session session) {
        return rest.exchange("/api/files/upload", HttpMethod.POST,
                new HttpEntity<>(multipart("logo.png", MediaType.IMAGE_PNG), session.headers()), JsonNode.class);
    }

    private MultiValueMap<String, HttpEntity<?>> multipart(String filename, MediaType contentType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", PNG_BYTES)
                .contentType(contentType)
                .filename(filename);
        return builder.build();
    }
}