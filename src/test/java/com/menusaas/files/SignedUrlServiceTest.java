package com.menusaas.files;

import com.menusaas.config.AppProperties;
import com.menusaas.files.security.SignedUrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * URLs firmadas: sin firma válida y vigente no se puede acceder a un archivo,
 * incluso conociendo el fileId.
 */
class SignedUrlServiceTest {

    private SignedUrlService signedUrlService;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                new AppProperties.Jwt("c2VjcmV0by1kZS1wcnVlYmEtc2VndXJvLWxvbmctZW5vdWdoLXNlY3JldA==", 15, 7),
                new AppProperties.Cors(java.util.List.of("http://localhost:4200")),
                "http://localhost:4200", "http://localhost:8080", "./uploads",
                new AppProperties.Security(false, 3600), new AppProperties.Payments("", "", "", ""));
        signedUrlService = new SignedUrlService(props);
    }

    @Test
    void buildSignedUrl_containsExpirationAndSignature() {
        String url = signedUrlService.buildSignedUrl("abc-123.png");

        assertThat(url).startsWith("http://localhost:8080/api/public/files/abc-123.png");
        assertThat(url).contains("exp=");
        assertThat(url).contains("sig=");
    }

    @Test
    void signedUrl_isValid_withCorrectSignatureAndExpiration() {
        String fileId = "abc-123.png";
        String url = signedUrlService.buildSignedUrl(fileId);
        long expiresAt = Long.parseLong(extractParam(url, "exp"));
        String sig = extractParam(url, "sig");

        assertThat(signedUrlService.isValid(fileId, expiresAt, sig)).isTrue();
    }

    @Test
    void signedUrl_isInvalid_whenExpired() {
        String fileId = "abc-123.png";
        long past = Instant.now().minusSeconds(10).getEpochSecond();
        String url = signedUrlService.buildSignedUrl(fileId);
        String sig = extractParam(url, "sig");

        assertThat(signedUrlService.isValid(fileId, past, sig)).isFalse();
    }

    @Test
    void signedUrl_isInvalid_whenSignatureTampered() {
        String fileId = "abc-123.png";
        String url = signedUrlService.buildSignedUrl(fileId);
        long expiresAt = Long.parseLong(extractParam(url, "exp"));
        String sig = extractParam(url, "sig");

        assertThat(signedUrlService.isValid(fileId, expiresAt, sig + "ff")).isFalse();
        assertThat(signedUrlService.isValid(fileId, expiresAt, "0".repeat(sig.length()))).isFalse();
    }

    @Test
    void signedUrl_isInvalid_whenFileIdChanged() {
        String url = signedUrlService.buildSignedUrl("abc-123.png");
        long expiresAt = Long.parseLong(extractParam(url, "exp"));
        String sig = extractParam(url, "sig");

        assertThat(signedUrlService.isValid("otro-archivo.png", expiresAt, sig)).isFalse();
    }

    @Test
    void toSignedUrlOrNull_returnsNullForBlank() {
        assertThat(signedUrlService.toSignedUrlOrNull(null)).isNull();
        assertThat(signedUrlService.toSignedUrlOrNull("  ")).isNull();
    }

    @Test
    void toSignedUrlOrNull_keepsExternalUrlsUntouched() {
        assertThat(signedUrlService.toSignedUrlOrNull("https://cdn.example.com/logo.png"))
                .isEqualTo("https://cdn.example.com/logo.png");
        assertThat(signedUrlService.toSignedUrlOrNull("/uploads/viejo.png"))
                .isEqualTo("/uploads/viejo.png");
    }

    @Test
    void toSignedUrlOrNull_signsStoredFileIds() {
        String url = signedUrlService.toSignedUrlOrNull("abc-123.png");
        assertThat(url).startsWith("http://localhost:8080/api/public/files/abc-123.png?exp=");
        assertThat(url).contains("&sig=");
    }

    private String extractParam(String url, String name) {
        Matcher matcher = Pattern.compile(name + "=([^&]+)").matcher(url);
        assertThat(matcher.find()).isTrue();
        return URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
    }
}