package com.menusaas.admin.service;

import com.menusaas.admin.dto.*;
import com.menusaas.files.security.SignedUrlService;
import com.menusaas.products.repository.ProductRepository;
import com.menusaas.restaurants.entity.Restaurant;
import com.menusaas.restaurants.repository.RestaurantRepository;
import com.menusaas.shared.api.ConflictException;
import com.menusaas.shared.api.ForbiddenException;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.shared.security.SecurityUtils;
import com.menusaas.subscriptions.entity.Plan;
import com.menusaas.subscriptions.entity.Subscription;
import com.menusaas.subscriptions.repository.PlanRepository;
import com.menusaas.subscriptions.repository.SubscriptionRepository;
import com.menusaas.users.entity.Role;
import com.menusaas.users.entity.User;
import com.menusaas.users.repository.RoleRepository;
import com.menusaas.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final SignedUrlService signedUrlService;

    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        long totalRestaurants = restaurantRepository.count();
        long activeRestaurants = restaurantRepository.countByActive(true);
        long totalUsers = userRepository.count();
        long activeSubscriptions = subscriptionRepository.countByStatus(Subscription.STATUS_ACTIVE);
        long totalProducts = productRepository.count();

        return new AdminStatsResponse(
                totalRestaurants,
                activeRestaurants,
                totalUsers,
                activeSubscriptions,
                totalProducts
        );
    }

    @Transactional(readOnly = true)
    public List<AdminRestaurantResponse> listRestaurants() {
        return restaurantRepository.findAllByOrderByIdDesc()
                .stream()
                .map(r -> {
                    long userCount = userRepository.countByRestaurantId(r.getId());
                    long productCount = productRepository.countByRestaurantId(r.getId());
                    String planName = subscriptionRepository.findFirstByRestaurantIdAndStatusOrderByCreatedAtDesc(
                                    r.getId(), Subscription.STATUS_ACTIVE)
                            .flatMap(s -> planRepository.findById(s.getPlanId()))
                            .map(Plan::getName)
                            .orElse("Sin plan");

                    String adminEmail = userRepository.findByRestaurantId(r.getId(), Role.RESTAURANT_ADMIN)
                            .stream()
                            .findFirst()
                            .map(User::getEmail)
                            .orElse("N/A");

                    return new AdminRestaurantResponse(
                            r.getId(),
                            r.getName(),
                            r.getSlug(),
                            signedUrlService.toSignedUrlOrNull(r.getLogoUrl()),
                            r.getPhone(),
                            r.getAddress(),
                            r.isActive(),
                            r.getCreatedAt(),
                            userCount,
                            productCount,
                            planName,
                            adminEmail
                    );
                })
                .toList();
    }

    @Transactional
    public AdminRestaurantResponse createRestaurant(AdminCreateRestaurantRequest request) {
        String email = request.adminEmail().trim().toLowerCase();
        String slug = request.slug().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Ya existe una cuenta con este correo");
        }
        if (restaurantRepository.existsBySlug(slug)) {
            throw new ConflictException("El slug '" + slug + "' ya está en uso");
        }

        Role role = roleRepository.findByName(Role.RESTAURANT_ADMIN)
                .orElseThrow(() -> new IllegalStateException("Rol RESTAURANT_ADMIN no configurado"));

        Restaurant restaurant = Restaurant.builder()
                .name(request.restaurantName().trim())
                .slug(slug)
                .active(true)
                .build();
        restaurant = restaurantRepository.save(restaurant);

        User user = User.builder()
                .name(request.adminName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.adminPassword()))
                .role(role)
                .restaurant(restaurant)
                .active(true)
                .build();
        userRepository.save(user);

        // Plan inicial (NEGOCODE por defecto o el especificado)
        String planCode = (request.planCode() != null && !request.planCode().isBlank())
                ? request.planCode()
                : "NEGOCODE";

        Plan plan = planRepository.findByCode(planCode)
                .orElseGet(() -> planRepository.findAll().stream().findFirst().orElse(null));

        if (plan != null) {
            subscriptionRepository.save(Subscription.builder()
                    .restaurantId(restaurant.getId())
                    .planId(plan.getId())
                    .status(Subscription.STATUS_ACTIVE)
                    .provider(Subscription.PROVIDER_MANUAL)
                    .startsAt(Instant.now())
                    .build());
        }

        log.info("Super Admin creó restaurante: slug={}, admin={}", slug, email);

        return new AdminRestaurantResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getSlug(),
                null,
                restaurant.getPhone(),
                restaurant.getAddress(),
                restaurant.isActive(),
                restaurant.getCreatedAt(),
                1L,
                0L,
                plan != null ? plan.getName() : "Gratis",
                email
        );
    }

    @Transactional
    public void toggleRestaurantActive(Long id, boolean active) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado"));
        restaurant.setActive(active);
        restaurantRepository.save(restaurant);
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers() {
        return userRepository.findAll()
                .stream()
                .map(u -> new AdminUserResponse(
                        u.getId(),
                        u.getName(),
                        u.getEmail(),
                        u.getRole().getName(),
                        u.isActive(),
                        u.getCreatedAt(),
                        u.getRestaurant() != null ? u.getRestaurant().getId() : null,
                        u.getRestaurant() != null ? u.getRestaurant().getName() : "Plataforma (Global)"
                ))
                .toList();
    }

    @Transactional
    public void toggleUserActive(Long id, boolean active) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (user.getId().equals(SecurityUtils.currentUser().getId()) && !active) {
            throw new ForbiddenException("No puede desactivarse a sí mismo");
        }

        user.setActive(active);
        userRepository.save(user);
    }
}
