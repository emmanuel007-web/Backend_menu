package com.menusaas.files.service;

import com.menusaas.config.AppProperties;
import com.menusaas.shared.api.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Almacenamiento local. La extensión y el MIME se derivan de los BYTES del
 * archivo (magic bytes), NO del nombre enviado por el cliente: un archivo
 * "malware.exe" con bytes de JPEG se guarda como .jpg, y un archivo que mienta
 * su Content-Type se rechaza.
 */
@Service
public class LocalFileStorageService implements FileStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    /** Mapa magic-bytes → (Content-Type, extensión). */
    private static final Map<String, String[]> MAGIC_BYTE_TYPES = Map.of(
            "ffd8ff", new String[]{"image/jpeg", ".jpg"},
            "89504e47", new String[]{"image/png", ".png"},
            "47494638", new String[]{"image/gif", ".gif"},   // GIF8
            "52494646", new String[]{"image/webp", ".webp"}  // RIFF....WEBP (se verifica más abajo)
    );

    private final Path uploadRoot;

    public LocalFileStorageService(AppProperties appProperties) {
        this.uploadRoot = Path.of(appProperties.uploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo crear el directorio de uploads", ex);
        }
    }

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("El archivo está vacío");
        }
        if (!isSupported(file)) {
            throw new BadRequestException("Formato no permitido. Use JPG, PNG, WEBP o GIF");
        }
        try {
            byte[] bytes = file.getBytes();
            String detectedType = detectContentType(bytes);
            if (!ALLOWED_TYPES.contains(detectedType)) {
                throw new BadRequestException("El contenido del archivo no es una imagen permitida");
            }

            String extension = extensionFor(detectedType);
            String filename = UUID.randomUUID() + extension;
            Path target = uploadRoot.resolve(filename).normalize();
            if (!target.startsWith(uploadRoot)) {
                throw new BadRequestException("Nombre de archivo inválido");
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return filename;
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo guardar el archivo", ex);
        }
    }

    @Override
    public StoredFile load(String fileId) {
        if (fileId == null || !fileId.matches("[A-Za-z0-9._-]+")) {
            throw new BadRequestException("Identificador de archivo inválido");
        }
        Path target = uploadRoot.resolve(fileId).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new BadRequestException("Identificador de archivo inválido");
        }
        if (!Files.isRegularFile(target)) {
            throw new BadRequestException("El archivo no existe");
        }
        try {
            return new StoredFile(fileId, Files.readAllBytes(target), contentTypeFor(fileId));
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo leer el archivo", ex);
        }
    }

    @Override
    public boolean isSupported(MultipartFile file) {
        String declared = file.getContentType();
        return declared == null || ALLOWED_TYPES.contains(declared);
    }

    /**
     * Detecta el Content-Type real a partir de los magic bytes.
     * Soporta JPEG, PNG, GIF y WEBP.
     */
    private String detectContentType(byte[] bytes) {
        if (bytes.length < 12) {
            return "application/octet-stream";
        }
        String hex = hexPrefix(bytes, 4);
        for (Map.Entry<String, String[]> entry : MAGIC_BYTE_TYPES.entrySet()) {
            if (hex.startsWith(entry.getKey())) {
                String[] candidate = entry.getValue();
                if ("image/webp".equals(candidate[0])) {
                    // RIFF + "WEBP" en los bytes 8..11
                    if (bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
                        return "image/webp";
                    }
                    return "application/octet-stream";
                }
                if ("image/gif".equals(candidate[0])) {
                    // GIF87a / GIF89a
                    if ((bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a') {
                        return "image/gif";
                    }
                    return "application/octet-stream";
                }
                return candidate[0];
            }
        }
        return "application/octet-stream";
    }

    private String hexPrefix(byte[] bytes, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n && i < bytes.length; i++) {
            sb.append(String.format("%02x", bytes[i]));
        }
        return sb.toString();
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".bin";
        };
    }

    private String contentTypeFor(String fileId) {
        String lower = fileId.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}