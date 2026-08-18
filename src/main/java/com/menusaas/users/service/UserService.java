package com.menusaas.users.service;

import com.menusaas.restaurants.repository.RestaurantRepository;
import com.menusaas.shared.api.ConflictException;
import com.menusaas.shared.api.ForbiddenException;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.shared.security.SecurityUtils;
import com.menusaas.users.dto.CreateUserRequest;
import com.menusaas.users.dto.UserResponse;
import com.menusaas.users.entity.Role;
import com.menusaas.users.entity.User;
import com.menusaas.users.repository.RoleRepository;
import com.menusaas.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RestaurantRepository restaurantRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserResponse> listMine() {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        return userRepository.findByRestaurantId(restaurantId, null)
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getMine(Long id) {
        return UserResponse.from(findScoped(id));
    }

    @Transactional
    public UserResponse createMine(CreateUserRequest request) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Ya existe un usuario con ese correo");
        }
        requireAdmin();

        String roleName = request.role() != null ? request.role() : Role.RESTAURANT_USER;
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Rol no configurado: " + roleName));

        User user = User.builder()
                .name(request.name().trim())
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .role(role)
                .restaurant(restaurantRepository.getReferenceById(restaurantId))
                .active(true)
                .build();
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void deleteMine(Long id) {
        requireAdmin();
        User user = findScoped(id);
        if (user.getId().equals(SecurityUtils.currentUser().getId())) {
            throw new ForbiddenException("No puede eliminarse a sí mismo");
        }
        userRepository.delete(user);
    }

    @Transactional
    public void toggleActiveMine(Long id, boolean active) {
        requireAdmin();
        User user = findScoped(id);
        if (user.getId().equals(SecurityUtils.currentUser().getId()) && !active) {
            throw new ForbiddenException("No puede desactivarse a sí mismo");
        }
        user.setActive(active);
        userRepository.save(user);
    }

    private User findScoped(Long id) {
        return userRepository.findById(id)
                .filter(u -> u.getRestaurant() != null
                        && u.getRestaurant().getId().equals(SecurityUtils.currentRestaurantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private void requireAdmin() {
        String role = SecurityUtils.currentUser().getRole();
        if (!Role.RESTAURANT_ADMIN.equals(role) && !Role.SUPER_ADMIN.equals(role)) {
            throw new ForbiddenException("Solo el administrador del restaurante puede gestionar usuarios");
        }
    }
}