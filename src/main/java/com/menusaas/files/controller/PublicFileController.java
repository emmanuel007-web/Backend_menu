package com.menusaas.files.controller;

import com.menusaas.files.security.SignedUrlService;
import com.menusaas.files.service.FileStorageService;
import com.menusaas.shared.api.BadRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

/**
 * Sirve imágenes con URL firmada: la URL lleva expiración + firma HMAC.
 * Sin una firma válida y vigente NO se puede acceder, incluso adivinando el fileId.
 */
@Tag(name = "Files", description = "Acceso público a imágenes mediante URLs firmadas")
@RestController
@RequestMapping("/api/public/files")
@RequiredArgsConstructor
public class PublicFileController {

    private final FileStorageService fileStorageService;
    private final SignedUrlService signedUrlService;

    @Operation(summary = "Servir imagen con URL firmada (expiración + HMAC)")
    @GetMapping("/{fileId}")
    public ResponseEntity<byte[]> serve(@PathVariable String fileId,
                                        @RequestParam long exp,
                                        @RequestParam String sig) {
        if (!signedUrlService.isValid(fileId, exp, sig)) {
            throw new BadRequestException("Enlace de imagen inválido o expirado");
        }
        FileStorageService.StoredFile stored = fileStorageService.load(fileId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + stored.fileId() + "\"")
                .body(stored.content());
    }
}