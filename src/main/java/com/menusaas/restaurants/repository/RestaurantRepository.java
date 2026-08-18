package com.menusaas.restaurants.repository;

import com.menusaas.restaurants.entity.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    Optional<Restaurant> findBySlug(String slug);

    boolean existsBySlug(String slug);
}