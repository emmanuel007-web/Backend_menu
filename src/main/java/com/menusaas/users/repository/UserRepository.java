package com.menusaas.users.repository;

import com.menusaas.users.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("""
            select u from User u
            where u.restaurant.id = :restaurantId
              and (:roleName is null or u.role.name = :roleName)
            order by u.name
            """)
    List<User> findByRestaurantId(Long restaurantId, String roleName);

    long countByRestaurantId(Long restaurantId);
}