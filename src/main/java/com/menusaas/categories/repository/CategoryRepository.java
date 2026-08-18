package com.menusaas.categories.repository;

import com.menusaas.categories.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByRestaurantIdOrderByPositionAsc(Long restaurantId);

    Optional<Category> findByIdAndRestaurantId(Long id, Long restaurantId);

    boolean existsByIdAndRestaurantId(Long id, Long restaurantId);

    boolean existsByRestaurantIdAndNameIgnoreCase(Long restaurantId, String name);

    long countByRestaurantId(Long restaurantId);
}