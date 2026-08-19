package com.menusaas.categories.service;

import com.menusaas.categories.dto.CategoryRequest;
import com.menusaas.categories.dto.CategoryResponse;
import com.menusaas.categories.entity.Category;
import com.menusaas.categories.repository.CategoryRepository;
import com.menusaas.products.repository.ProductRepository;
import com.menusaas.shared.api.ConflictException;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Page<CategoryResponse> listMine(Pageable pageable) {
        return categoryRepository.findByRestaurantIdOrderByPositionAsc(SecurityUtils.currentRestaurantId(), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CategoryResponse getMine(Long id) {
        return toResponse(findScoped(id));
    }

    @Transactional
    public CategoryResponse createMine(CategoryRequest request) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        if (categoryRepository.existsByRestaurantIdAndNameIgnoreCase(restaurantId, request.name().trim())) {
            throw new ConflictException("Ya existe una categoría con ese nombre");
        }
        Category category = Category.builder()
                .restaurantId(restaurantId)
                .name(request.name().trim())
                .description(request.description())
                .position(request.position() != null ? request.position() : (int) categoryRepository.countByRestaurantId(restaurantId) + 1)
                .active(request.active() == null || request.active())
                .build();
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateMine(Long id, CategoryRequest request) {
        Category category = findScoped(id);
        if (!category.getName().equalsIgnoreCase(request.name().trim())
                && categoryRepository.existsByRestaurantIdAndNameIgnoreCase(category.getRestaurantId(), request.name().trim())) {
            throw new ConflictException("Ya existe una categoría con ese nombre");
        }
        category.setName(request.name().trim());
        if (request.description() != null) category.setDescription(request.description());
        if (request.position() != null) category.setPosition(request.position());
        if (request.active() != null) category.setActive(request.active());
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void deleteMine(Long id) {
        Category category = findScoped(id);
        productRepository.deleteByCategoryIdAndRestaurantId(category.getId(), category.getRestaurantId());
        categoryRepository.delete(category);
    }

    private Category findScoped(Long id) {
        return categoryRepository.findByIdAndRestaurantId(id, SecurityUtils.currentRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada"));
    }

    private CategoryResponse toResponse(Category c) {
        return new CategoryResponse(
                c.getId(), c.getRestaurantId(), c.getName(), c.getDescription(),
                c.getPosition(), c.isActive(), c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}