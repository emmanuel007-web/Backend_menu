package com.menusaas.files.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Abstracción de almacenamiento de imágenes.
 * La implementación local guarda en disco; el acceso público se hace mediante
 * URLs firmadas con expiración (nunca sirviendo el directorio directamente).
 */
public interface FileStorageService {

    /**
     * Almacena la imagen y devuelve el fileId (nombre seguro generado por el servidor).
     */
    String store(MultipartFile file);

    /**
     * Carga un archivo ya almacenado (fileId). Lanza BadRequestException si no existe.
     */
    StoredFile load(String fileId);

    boolean isSupported(MultipartFile file);

    /**
     * Archivo almacenado junto con su Content-Type detectado.
     */
    record StoredFile(String fileId, byte[] content, String contentType) {
    }
}