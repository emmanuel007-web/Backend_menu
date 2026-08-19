package com.menusaas.subscriptions.repository;

import com.menusaas.subscriptions.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findFirstByRestaurantIdAndStatusOrderByCreatedAtDesc(Long restaurantId, String status);

    List<Subscription> findByRestaurantIdAndStatusInOrderByCreatedAtDesc(Long restaurantId, Collection<String> statuses);

    List<Subscription> findByStatusAndEndsAtBefore(String status, Instant before);

    Optional<Subscription> findByProviderReference(String providerReference);
}