package com.menusaas.menus.controller;

import com.menusaas.menus.dto.PublicMenuResponse;
import com.menusaas.menus.service.PublicMenuService;
import com.menusaas.products.repository.ProductRepository;
import com.menusaas.restaurants.dto.DirectoryRestaurantResponse;
import com.menusaas.restaurants.entity.Restaurant;
import com.menusaas.restaurants.repository.RestaurantRepository;
import com.menusaas.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Tag(name = "Public Menu", description = "Menú público por slug — sin autenticación")
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicMenuController {

    private final PublicMenuService publicMenuService;
    private final RestaurantRepository restaurantRepository;
    private final ProductRepository productRepository;

    @Operation(summary = "Obtener el menú público de un restaurante")
    @GetMapping("/menu/{slug}")
    public ApiResponse<PublicMenuResponse> getMenu(@PathVariable String slug) {
        return ApiResponse.ok(publicMenuService.getBySlug(slug));
    }

    @Operation(summary = "Directorio público de restaurantes activos (módulo Explore)")
    @GetMapping("/restaurants")
    public ApiResponse<List<DirectoryRestaurantResponse>> getDirectory() {
        List<Restaurant> restaurants = restaurantRepository.findAllByActiveTrueOrderByNameAsc();
        Map<Long, Long> productCounts = productRepository.countAvailableGroupedByRestaurant().stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        List<DirectoryRestaurantResponse> directory = restaurants.stream()
                .map(r -> new DirectoryRestaurantResponse(
                        r.getId(),
                        r.getName(),
                        r.getSlug(),
                        r.getDescription(),
                        r.getLogoUrl(),
                        r.getPhone(),
                        r.getWhatsapp(),
                        r.getAddress(),
                        r.isOpen(),
                        productCounts.getOrDefault(r.getId(), 0L)))
                .toList();
        return ApiResponse.ok(directory);
    }
}