package com.menusaas.products.service;

import com.menusaas.categories.repository.CategoryRepository;
import com.menusaas.products.dto.ProductRequest;
import com.menusaas.products.dto.ProductResponse;
import com.menusaas.products.entity.Product;
import com.menusaas.products.repository.ProductRepository;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<ProductResponse> listMine(Long categoryId) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        List<Product> products = categoryId != null
                ? productRepository.findByCategoryIdAndRestaurantIdOrderByPositionAsc(categoryId, restaurantId)
                : productRepository.findByRestaurantIdOrderByPositionAsc(restaurantId);
        return products.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getMine(Long id) {
        return toResponse(findScoped(id));
    }

    @Transactional
    public ProductResponse createMine(ProductRequest request) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        validateCategoryBelongsToTenant(request.categoryId(), restaurantId);

        Product product = Product.builder()
                .restaurantId(restaurantId)
                .categoryId(request.categoryId())
                .name(request.name().trim())
                .description(request.description())
                .price(request.price())
                .imageUrl(request.imageUrl())
                .available(request.available() == null || request.available())
                .position(request.position() != null ? request.position() : 0)
                .build();
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse updateMine(Long id, ProductRequest request) {
        Product product = findScoped(id);
        validateCategoryBelongsToTenant(request.categoryId(), product.getRestaurantId());

        product.setCategoryId(request.categoryId());
        product.setName(request.name().trim());
        if (request.description() != null) product.setDescription(request.description());
        product.setPrice(request.price());
        if (request.imageUrl() != null) product.setImageUrl(request.imageUrl());
        if (request.available() != null) product.setAvailable(request.available());
        if (request.position() != null) product.setPosition(request.position());
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public void deleteMine(Long id) {
        Product product = findScoped(id);
        productRepository.delete(product);
    }

    private Product findScoped(Long id) {
        return productRepository.findByIdAndRestaurantId(id, SecurityUtils.currentRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));
    }

    /**
     * La categoría debe pertenecer al mismo tenant, de lo contrario se rechaza
     * (evita mover productos entre restaurantes mediante categoryId).
     */
    private void validateCategoryBelongsToTenant(Long categoryId, Long restaurantId) {
        if (!categoryRepository.existsByIdAndRestaurantId(categoryId, restaurantId)) {
            throw new ResourceNotFoundException("Categoría no encontrada en este restaurante");
        }
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(
                p.getId(), p.getRestaurantId(), p.getCategoryId(), p.getName(), p.getDescription(),
                p.getPrice(), p.getImageUrl(), p.isAvailable(), p.getPosition(),
                p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}