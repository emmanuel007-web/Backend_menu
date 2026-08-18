package com.menusaas.menus.controller;

import com.menusaas.menus.dto.PublicMenuResponse;
import com.menusaas.menus.service.PublicMenuService;
import com.menusaas.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Public Menu", description = "Menú público por slug — sin autenticación")
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicMenuController {

    private final PublicMenuService publicMenuService;

    @Operation(summary = "Obtener el menú público de un restaurante")
    @GetMapping("/menu/{slug}")
    public ApiResponse<PublicMenuResponse> getMenu(@PathVariable String slug) {
        return ApiResponse.ok(publicMenuService.getBySlug(slug));
    }
}