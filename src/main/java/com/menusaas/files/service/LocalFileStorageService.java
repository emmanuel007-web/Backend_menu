package com.menusaas.files.service;

import com.menusaas.config.AppProperties;
import com.menusaas.shared.api.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * Almacenamiento local para el MVP. Las imágenes se sirven desde /uploads/**.
 */
@Service
public class LocalFileStorageService implements FileStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private final Path uploadRoot;
    private final String baseUrl;

    public LocalFileStorageService(AppProperties appProperties) {
        this.uploadRoot = Path.of(appProperties.uploadDir()).toAbsolutePath().normalize();
        this.baseUrl = "/uploads";
        try {
            Files.createDirectories(uploadRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo crear el directorio de uploads", ex);
        }
    }

    @Override
    public String store(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BadRequestException("El archivo está vacío");
        }
        if (!isSupported(file)) {
            throw new BadRequestException("Formato no permitido. Use JPG, PNG, WEBP o GIF");
        }
        try {
            String extension = extensionOf(file.getOriginalFilename());
            String filename = UUID.randomUUID() + extension;
            Path target = uploadRoot.resolve(filename).normalize();
            if (!target.startsWith(uploadRoot)) {
                throw new BadRequestException("Nombre de archivo inválido");
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return baseUrl + "/" + filename;
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo guardar el archivo", ex);
        }
    }

    @Override
    public boolean isSupported(MultipartFile file) {
        return ALLOWED_TYPES.contains(file.getContentType());
    }

    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".png";
        }
        return filename.substring(filename.lastIndexOf('.')).toLowerCase();
    }
}