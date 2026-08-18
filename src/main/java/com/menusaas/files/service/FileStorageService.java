package com.menusaas.files.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Abstracción de almacenamiento de imágenes.
 * MVP: implementación local. Posteriormente: Cloudinary/S3 sin tocar los servicios.
 */
public interface FileStorageService {

    /**
     * Almacena la imagen y devuelve la URL pública para guardar en la base de datos.
     */
    String store(MultipartFile file);

    boolean isSupported(MultipartFile file);
}