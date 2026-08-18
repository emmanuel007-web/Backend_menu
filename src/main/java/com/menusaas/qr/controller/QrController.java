package com.menusaas.qr.controller;

import com.menusaas.config.AppProperties;
import com.menusaas.qr.service.QrCodeService;
import com.menusaas.restaurants.repository.RestaurantRepository;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * QR del propio restaurante (tenant del JWT).
 * El QR apunta a la app web (menu.tumarca.com/{slug}), nunca a la API.
 */
@Tag(name = "QR", description = "Descarga del código QR del menú (restaurante autenticado)")
@RestController
@RequestMapping("/api/qr")
@RequiredArgsConstructor
public class QrController {

    private final QrCodeService qrCodeService;
    private final RestaurantRepository restaurantRepository;
    private final AppProperties appProperties;

    @Operation(summary = "Descargar QR del menú en PNG")
    @GetMapping("/png")
    public ResponseEntity<byte[]> downloadPng() {
        byte[] png = qrCodeService.generatePng(buildMenuUrl());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"qr-menu.png\"")
                .body(png);
    }

    @Operation(summary = "Descargar QR del menú en PDF (A4)")
    @GetMapping("/pdf")
    public ResponseEntity<byte[]> downloadPdf() {
        byte[] pdf = qrCodeService.generatePdf(buildMenuUrl());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"qr-menu.pdf\"")
                .body(pdf);
    }

    @Operation(summary = "Obtener la URL pública del menú (para imprimir o mostrar)")
    @GetMapping("/url")
    public com.menusaas.shared.api.ApiResponse<java.util.Map<String, String>> getMenuUrl() {
        return com.menusaas.shared.api.ApiResponse.ok(java.util.Map.of("url", buildMenuUrl()));
    }

    private String buildMenuUrl() {
        String base = appProperties.appBaseUrl().replaceAll("/+$", "");
        return base + "/menu/" + slugOfCurrentRestaurant();
    }

    private String slugOfCurrentRestaurant() {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        return restaurantRepository.findById(restaurantId)
                .map(r -> r.getSlug())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado"));
    }
}