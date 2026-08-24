package com.menusaas.restaurants.repository;

import com.menusaas.restaurants.entity.Restaurant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    Optional<Restaurant> findBySlug(String slug);

    /**
     * Lock pesimista sobre la fila del restaurante: serializa la creación de
     * pedidos por tenant (evita duplicar el consecutivo de order_number).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Restaurant r where r.slug = :slug")
    Optional<Restaurant> findBySlugForUpdate(@Param("slug") String slug);

    boolean existsBySlug(String slug);

    long countByActive(boolean active);

    java.util.List<Restaurant> findAllByOrderByIdDesc();

    java.util.List<Restaurant> findAllByActiveTrueOrderByNameAsc();
}