package com.menusaas.users;

import com.menusaas.auth.security.UserPrincipal;
import com.menusaas.restaurants.entity.Restaurant;
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
import com.menusaas.users.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas de negocio del mantenimiento de usuarios del restaurante:
 * alcance por restaurante (no se puede leer/mutar usuarios de otro),
 * solo administradores gestionan usuarios, y nadie se elimina/desactiva a sí mismo.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RestaurantRepository restaurantRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private UserService userService;

    private static User user(long id, long restaurantId, String role, String email) {
        return User.builder().id(id).name("Usuario").email(email).active(true)
                .role(new Role(null, role, null))
                .restaurant(Restaurant.builder().id(restaurantId).name("Rest").slug("rest").build())
                .build();
    }

    private static User admin(long id, long restaurantId) {
        return user(id, restaurantId, Role.RESTAURANT_ADMIN, "admin@rest.com");
    }

    private static UserPrincipal principal(User user) {
        return UserPrincipal.from(user);
    }

    @Test
    void listMine_returnsOnlyUsersOfMyRestaurant() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            when(userRepository.findByRestaurantId(1L, null))
                    .thenReturn(List.of(user(1, 1, Role.RESTAURANT_USER, "u1@rest.com")));

            List<UserResponse> users = userService.listMine();

            assertThat(users).hasSize(1);
            assertThat(users.get(0).email()).isEqualTo("u1@rest.com");
        }
    }

    @Test
    void getMine_fromOtherRestaurant_throws404() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            when(userRepository.findById(99L)).thenReturn(Optional.of(user(99, 2, Role.RESTAURANT_USER, "otro@rest.com")));

            assertThatThrownBy(() -> userService.getMine(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    void createMine_duplicateEmail_throws409() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            when(userRepository.existsByEmail("dup@rest.com")).thenReturn(true);

            assertThatThrownBy(() -> userService.createMine(
                    new CreateUserRequest("Nuevo", "DUP@rest.com", "StrongPass123!", Role.RESTAURANT_USER)))
                    .isInstanceOf(ConflictException.class);
        }
    }

    @Test
    void createMine_nonAdmin_throws403() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            security.when(SecurityUtils::currentUser)
                    .thenReturn(principal(user(5, 1, Role.RESTAURANT_USER, "empleado@rest.com")));
            when(userRepository.existsByEmail("nuevo@rest.com")).thenReturn(false);

            assertThatThrownBy(() -> userService.createMine(
                    new CreateUserRequest("Nuevo", "nuevo@rest.com", "StrongPass123!", Role.RESTAURANT_USER)))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Test
    void createMine_asAdmin_createsUserWithDefaultRole() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            security.when(SecurityUtils::currentUser).thenReturn(principal(admin(1, 1)));
            when(userRepository.existsByEmail("nuevo@rest.com")).thenReturn(false);
            when(roleRepository.findByName(Role.RESTAURANT_USER))
                    .thenReturn(Optional.of(new Role(null, Role.RESTAURANT_USER, null)));
            when(passwordEncoder.encode("StrongPass123!")).thenReturn("hash");
            when(restaurantRepository.getReferenceById(1L))
                    .thenReturn(Restaurant.builder().id(1L).build());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UserResponse created = userService.createMine(
                    new CreateUserRequest("Nuevo", "nuevo@rest.com", "StrongPass123!", null));

            assertThat(created.email()).isEqualTo("nuevo@rest.com");
            verify(passwordEncoder).encode("StrongPass123!");
        }
    }

    @Test
    void deleteMine_selfDeletion_throws403() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            security.when(SecurityUtils::currentUser).thenReturn(principal(admin(1, 1)));
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin(1, 1)));

            assertThatThrownBy(() -> userService.deleteMine(1L))
                    .isInstanceOf(ForbiddenException.class);
            verify(userRepository, never()).delete(any());
        }
    }

    @Test
    void deleteMine_otherUser_deletesIt() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            security.when(SecurityUtils::currentUser).thenReturn(principal(admin(1, 1)));
            when(userRepository.findById(2L)).thenReturn(Optional.of(user(2, 1, Role.RESTAURANT_USER, "u2@rest.com")));

            userService.deleteMine(2L);

            verify(userRepository).delete(any(User.class));
        }
    }

    @Test
    void toggleActiveMine_selfDeactivation_throws403() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            security.when(SecurityUtils::currentUser).thenReturn(principal(admin(1, 1)));
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin(1, 1)));

            assertThatThrownBy(() -> userService.toggleActiveMine(1L, false))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Test
    void toggleActiveMine_otherUser_togglesAndSaves() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::currentRestaurantId).thenReturn(1L);
            security.when(SecurityUtils::currentUser).thenReturn(principal(admin(1, 1)));
            User target = user(2, 1, Role.RESTAURANT_USER, "u2@rest.com");
            when(userRepository.findById(2L)).thenReturn(Optional.of(target));

            userService.toggleActiveMine(2L, false);

            assertThat(target.isActive()).isFalse();
            verify(userRepository).save(target);
        }
    }
}