package com.menusaas.orders.repository;

import com.menusaas.orders.entity.Order;
import com.menusaas.orders.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);

    List<Order> findByRestaurantIdAndStatusOrderByCreatedAtDesc(Long restaurantId, OrderStatus status);

    Optional<Order> findByIdAndRestaurantId(Long id, Long restaurantId);

    long countByRestaurantId(Long restaurantId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.restaurantId = :restaurantId")
    long countOrdersForRestaurant(@Param("restaurantId") Long restaurantId);
}
