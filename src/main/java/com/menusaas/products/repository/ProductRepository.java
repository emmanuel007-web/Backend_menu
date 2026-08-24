package com.menusaas.products.repository;

import com.menusaas.products.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByRestaurantIdOrderByPositionAsc(Long restaurantId, Pageable pageable);

    @Query("select p.restaurantId, count(p) from Product p where p.available = true group by p.restaurantId")
    List<Object[]> countAvailableGroupedByRestaurant();

    Page<Product> findByCategoryIdAndRestaurantIdOrderByPositionAsc(Long categoryId, Long restaurantId, Pageable pageable);

    Optional<Product> findByIdAndRestaurantId(Long id, Long restaurantId);

    @Modifying
    @Query("delete from Product p where p.categoryId = :categoryId and p.restaurantId = :restaurantId")
    int deleteByCategoryIdAndRestaurantId(@Param("categoryId") Long categoryId, @Param("restaurantId") Long restaurantId);

    @Query("""
            select p from Product p
            where p.categoryId = :categoryId
              and p.restaurantId = :restaurantId
              and (:onlyAvailable = false or p.available = true)
            order by p.position asc, p.name asc
            """)
    List<Product> findByCategoryScoped(@Param("categoryId") Long categoryId,
                                       @Param("restaurantId") Long restaurantId,
                                       @Param("onlyAvailable") boolean onlyAvailable);

    long countByRestaurantId(Long restaurantId);
}