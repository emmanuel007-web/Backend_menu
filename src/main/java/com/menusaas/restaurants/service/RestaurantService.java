package com.menusaas.restaurants.service;

import com.menusaas.files.security.SignedUrlService;
import com.menusaas.restaurants.dto.RestaurantRequest;
import com.menusaas.restaurants.dto.RestaurantResponse;
import com.menusaas.restaurants.entity.Restaurant;
import com.menusaas.restaurants.repository.RestaurantRepository;
import com.menusaas.shared.api.ConflictException;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.users.entity.Role;
import com.menusaas.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final SignedUrlService signedUrlService;

    /**
     * Un usuario RESTAURANT_* solo puede acceder a SU restaurante (tenant del JWT).
     * SUPER_ADMIN puede acceder por id explícito.
     */
    @Transactional(readOnly = true)
    public RestaurantResponse getMine() {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        return toResponse(findByIdOrThrow(restaurantId));
    }

    @Transactional(readOnly = true)
    public RestaurantResponse getById(Long id) {
        requireSuperAdmin();
        return toResponse(findByIdOrThrow(id));
    }

    @Transactional
    public RestaurantResponse updateMine(RestaurantRequest request) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        Restaurant restaurant = findByIdOrThrow(restaurantId);
        return toResponse(update(restaurant, request));
    }

    @Transactional
    public RestaurantResponse setOpenMine(boolean open) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        Restaurant restaurant = findByIdOrThrow(restaurantId);
        restaurant.setOpen(open);
        return toResponse(restaurantRepository.save(restaurant));
    }

    @Transactional
    public RestaurantResponse updateById(Long id, RestaurantRequest request) {
        requireSuperAdmin();
        Restaurant restaurant = findByIdOrThrow(id);
        return toResponse(update(restaurant, request));
    }

    @Transactional
    public void deleteById(Long id) {
        requireSuperAdmin();
        Restaurant restaurant = findByIdOrThrow(id);
        restaurant.setActive(false);
        restaurantRepository.save(restaurant);
    }

    private Restaurant update(Restaurant restaurant, RestaurantRequest request) {
        String slug = request.slug().trim();
        if (!slug.equals(restaurant.getSlug()) && restaurantRepository.existsBySlug(slug)) {
            throw new ConflictException("El slug '" + slug + "' ya está en uso");
        }
        restaurant.setName(request.name().trim());
        restaurant.setSlug(slug);
        restaurant.setLogoUrl(request.logoUrl());
        restaurant.setDescription(request.description());
        restaurant.setPhone(request.phone());
        restaurant.setAddress(request.address());
        restaurant.setWhatsapp(request.whatsapp());
        restaurant.setInstagram(request.instagram());
        restaurant.setFacebook(request.facebook());
        if (request.active() != null) {
            restaurant.setActive(request.active());
        }
        if (request.open() != null) {
            restaurant.setOpen(request.open());
        }
        return restaurantRepository.save(restaurant);
    }

    private Restaurant findByIdOrThrow(Long id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado"));
    }

    private void requireSuperAdmin() {
        if (!Role.SUPER_ADMIN.equals(SecurityUtils.currentUser().getRole())) {
            throw new com.menusaas.shared.api.ForbiddenException("Solo el super administrador puede realizar esta operación");
        }
    }

    private RestaurantResponse toResponse(Restaurant r) {
        return new RestaurantResponse(
                r.getId(), r.getName(), r.getSlug(), signedUrlService.toSignedUrlOrNull(r.getLogoUrl()), r.getDescription(),
                r.getPhone(), r.getAddress(), r.getWhatsapp(), r.getInstagram(), r.getFacebook(),
                r.isActive(), r.isOpen(), r.getCreatedAt(), r.getUpdatedAt()
        );
    }
}