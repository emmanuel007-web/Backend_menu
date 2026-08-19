package com.menusaas.menus.service;

import com.menusaas.categories.entity.Category;
import com.menusaas.categories.repository.CategoryRepository;
import com.menusaas.files.security.SignedUrlService;
import com.menusaas.menus.dto.PublicMenuResponse;
import com.menusaas.products.entity.Product;
import com.menusaas.products.repository.ProductRepository;
import com.menusaas.restaurants.entity.Restaurant;
import com.menusaas.restaurants.repository.RestaurantRepository;
import com.menusaas.shared.api.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Menú público: sin autenticación, identificado por slug.
 * Las imágenes se sirven con URLs firmadas con expiración.
 */
@Service
@RequiredArgsConstructor
public class PublicMenuService {

    private final RestaurantRepository restaurantRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final SignedUrlService signedUrlService;

    @Transactional(readOnly = true)
    public PublicMenuResponse getBySlug(String slug) {
        Restaurant restaurant = restaurantRepository.findBySlug(slug)
                .filter(Restaurant::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Menú no encontrado"));

        List<Category> categories = categoryRepository.findAllByRestaurantIdOrderByPositionAsc(restaurant.getId())
                .stream()
                .filter(Category::isActive)
                .toList();

        List<PublicMenuResponse.CategoryInfo> categoryInfos = categories.stream()
                .map(category -> {
                    List<PublicMenuResponse.ProductInfo> products = productRepository
                            .findByCategoryScoped(category.getId(), restaurant.getId(), true)
                            .stream()
                            .map(this::toProductInfo)
                            .toList();
                    return new PublicMenuResponse.CategoryInfo(
                            category.getId(), category.getName(), category.getDescription(),
                            category.getPosition(), products
                    );
                })
                .toList();

        return new PublicMenuResponse(
                new PublicMenuResponse.RestaurantInfo(
                        restaurant.getName(), restaurant.getSlug(),
                        signedUrlService.toSignedUrlOrNull(restaurant.getLogoUrl()),
                        restaurant.getDescription(), restaurant.getPhone(), restaurant.getAddress(),
                        restaurant.getWhatsapp(), restaurant.getInstagram(), restaurant.getFacebook()
                ),
                categoryInfos
        );
    }

    private PublicMenuResponse.ProductInfo toProductInfo(Product p) {
        return new PublicMenuResponse.ProductInfo(
                p.getId(), p.getName(), p.getDescription(), p.getPrice(),
                signedUrlService.toSignedUrlOrNull(p.getImageUrl()), p.isAvailable()
        );
    }
}