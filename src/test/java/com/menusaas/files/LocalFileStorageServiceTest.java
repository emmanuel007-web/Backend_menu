package com.menusaas.files;

import com.menusaas.config.AppProperties;
import com.menusaas.files.service.FileStorageService;
import com.menusaas.files.service.LocalFileStorageService;
import com.menusaas.shared.api.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Seguridad del almacenamiento de archivos:
 * - El MIME se valida desde los BYTES (magic bytes), no del nombre ni del header.
 * - Un nombre manipulado (path traversal) no puede escapar del directorio.
 * - Archivos que mienten su Content-Type se rechazan.
 */
class LocalFileStorageServiceTest {

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
            0x49, 0x48, 0x44, 0x52
    };
    private static final byte[] JPEG_BYTES = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
    };
    private static final byte[] EXE_BYTES = {0x4D, 0x5A, 0x50, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

    @TempDir
    Path tempDir;

    private LocalFileStorageService storage;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                new AppProperties.Jwt("c2VjcmV0by1kZS1wcnVlYmEtc2VndXJvLWxvbmctZW5vdWdoLXNlY3JldA==", 15, 7),
                new AppProperties.Cors(java.util.List.of("http://localhost:4200")),
                "http://localhost:4200", "http://localhost:8080", tempDir.toString(),
                new AppProperties.Security(false, 3600), new AppProperties.Payments("", "", "", ""));
        storage = new LocalFileStorageService(props);
    }

    @Test
    void store_validPng_isStoredWithServerGeneratedName() {
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", PNG_BYTES);

        String fileId = storage.store(file);

        assertThat(fileId).matches("[0-9a-f-]{36}\\.png");
        assertThat(Files.exists(tempDir.resolve(fileId))).isTrue();
    }

    @Test
    void store_clientSendsExecutable_namedPng_rejectedByMagicBytes() {
        // El nombre dice .png y el Content-Type dice image/png, pero los bytes son un EXE.
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", EXE_BYTES);

        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void store_clientDeclaresDisallowedMime_rejected() {
        MockMultipartFile file = new MockMultipartFile("file", "logo.jpg", "text/html", PNG_BYTES);

        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void store_pathTraversalInFilename_cannotEscapeUploadDir() {
        // El nombre original NO se usa para nada: el fileId lo genera el servidor.
        MockMultipartFile file = new MockMultipartFile("file", "../../etc/passwd.png", "image/png", PNG_BYTES);

        String fileId = storage.store(file);

        assertThat(fileId).matches("[0-9a-f-]{36}\\.png");
        assertThat(tempDir.resolve(fileId).normalize().startsWith(tempDir.toAbsolutePath().normalize())).isTrue();
        assertThat(Files.exists(Path.of("/etc/passwd"))).isTrue(); // no fue sobrescrito, obviamente
    }

    @Test
    void load_fileIdWithSlashes_rejected() {
        assertThatThrownBy(() -> storage.load("../secreto"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void load_nonexistentFile_throws() {
        assertThatThrownBy(() -> storage.load("no-existe.png"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void load_storedFile_returnsContentAndDetectedType() {
        MockMultipartFile file = new MockMultipartFile("file", "img.png", "image/png", PNG_BYTES);
        String fileId = storage.store(file);

        FileStorageService.StoredFile stored = storage.load(fileId);

        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.content()).isEqualTo(PNG_BYTES);
    }

    @Test
    void store_jpegBytesWithExeName_getsJpgExtension() {
        // Cliente sin Content-Type declarado y nombre engañoso (.exe): los magic
        // bytes mandan → se guarda como .jpg con nombre generado por el servidor.
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", null, JPEG_BYTES);

        String fileId = storage.store(file);

        assertThat(fileId).matches("[0-9a-f-]{36}\\.jpg");
    }
}