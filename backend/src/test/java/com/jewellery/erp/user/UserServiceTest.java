package com.jewellery.erp.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jewellery.erp.auth.service.RefreshTokenService;
import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.DuplicateResourceException;
import com.jewellery.erp.permission.repository.PermissionRepository;
import com.jewellery.erp.permission.service.PermissionService;
import com.jewellery.erp.role.entity.Role;
import com.jewellery.erp.role.repository.RoleRepository;
import com.jewellery.erp.security.UserPrincipal;
import com.jewellery.erp.user.dto.CreateUserRequest;
import com.jewellery.erp.user.dto.ResetPasswordRequest;
import com.jewellery.erp.user.entity.User;
import com.jewellery.erp.user.mapper.UserMapper;
import com.jewellery.erp.user.repository.UserRepository;
import com.jewellery.erp.user.service.UserService;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The guard rails around user administration - the ones that stop an
 * installation locking itself out, and the ones that make a narrowing change
 * take effect immediately.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private PermissionService permissionService;
    @Mock private RefreshTokenService refreshTokenService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4); // fast for tests
    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(
                userRepository,
                roleRepository,
                permissionRepository,
                permissionService,
                refreshTokenService,
                passwordEncoder,
                new UserMapper());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void signInAs(Long userId, String username) {
        UserPrincipal principal =
                UserPrincipal.fromClaims(userId, username, username, Set.of("ROLE_ADMIN"), Set.of("USER_EDIT"));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
    }

    private static User administrator(Long id, String username) {
        Role adminRole = new Role();
        adminRole.setId(1L);
        adminRole.setName(Role.ADMIN);

        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setFullName("Admin " + username);
        user.setPasswordHash("hash");
        user.setActive(true);
        user.setRoles(new LinkedHashSet<>(Set.of(adminRole)));
        return user;
    }

    private static User staff(Long id, String username) {
        Role userRole = new Role();
        userRole.setId(2L);
        userRole.setName(Role.USER);

        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setFullName("Staff " + username);
        user.setPasswordHash("hash");
        user.setActive(true);
        user.setRoles(new LinkedHashSet<>(Set.of(userRole)));
        return user;
    }

    @Test
    @DisplayName("an administrator cannot deactivate their own account")
    void refusesSelfDeactivation() {
        signInAs(1L, "admin");
        when(userRepository.findById(1L)).thenReturn(Optional.of(administrator(1L, "admin")));

        assertThatThrownBy(() -> service.updateStatus(1L, false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("deactivate your own account");
    }

    @Test
    @DisplayName("the last active administrator cannot be deactivated")
    void refusesDeactivatingLastAdministrator() {
        signInAs(1L, "admin");
        when(userRepository.findById(2L)).thenReturn(Optional.of(administrator(2L, "owner")));
        when(userRepository.countOtherActiveAdministrators(2L)).thenReturn(0L);

        assertThatThrownBy(() -> service.updateStatus(2L, false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("last active administrator");

        verify(refreshTokenService, never()).revokeAllForUser(anyLong());
    }

    @Test
    @DisplayName("deactivating a user ends their live sessions")
    void deactivationRevokesSessions() {
        signInAs(1L, "admin");
        User target = staff(3L, "staff1");
        when(userRepository.findById(3L)).thenReturn(Optional.of(target));

        service.updateStatus(3L, false);

        assertThat(target.isActive()).isFalse();
        verify(refreshTokenService).revokeAllForUser(3L);
    }

    @Test
    @DisplayName("a duplicate username is rejected before any row is written")
    void refusesDuplicateUsername() {
        when(userRepository.existsByUsernameIgnoreCase("staff1")).thenReturn(true);

        CreateUserRequest request = new CreateUserRequest(
                "staff1", "Staff One", null, null, "Passw0rd1", null, null, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already taken");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("a new user's password is stored as a BCrypt hash, never in clear text")
    void hashesPasswordOnCreate() {
        Role userRole = new Role();
        userRole.setId(2L);
        userRole.setName(Role.USER);

        when(userRepository.existsByUsernameIgnoreCase("staff1")).thenReturn(false);
        when(roleRepository.findByName(Role.USER)).thenReturn(Optional.of(userRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateUserRequest request = new CreateUserRequest(
                "staff1", "Staff One", null, null, "Passw0rd1", null, null, true);

        service.create(request);

        org.mockito.ArgumentCaptor<User> saved = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash())
                .isNotEqualTo("Passw0rd1")
                .startsWith("$2");
        assertThat(passwordEncoder.matches("Passw0rd1", saved.getValue().getPasswordHash())).isTrue();
        assertThat(saved.getValue().isMustChangePassword()).isTrue();
    }

    @Test
    @DisplayName("a password reset ends every session that user had open")
    void passwordResetRevokesSessions() {
        signInAs(1L, "admin");
        User target = staff(3L, "staff1");
        when(userRepository.findById(3L)).thenReturn(Optional.of(target));

        service.resetPassword(3L, new ResetPasswordRequest("N3wPassword", true));

        assertThat(passwordEncoder.matches("N3wPassword", target.getPasswordHash())).isTrue();
        assertThat(target.isMustChangePassword()).isTrue();
        verify(refreshTokenService).revokeAllForUser(3L);
    }

    @Test
    @DisplayName("a user who has already signed in is deactivated, never deleted")
    void refusesDeletingUserWithHistory() {
        signInAs(1L, "admin");
        User target = staff(3L, "staff1");
        target.setLastLoginAt(Instant.now());
        when(userRepository.findById(3L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.delete(3L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Deactivate the account instead");

        // Typed matcher: the repository also inherits delete(Specification), so a
        // bare any() cannot tell the two overloads apart.
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    @DisplayName("an administrator cannot edit their own permissions")
    void refusesEditingOwnPermissions() {
        signInAs(1L, "admin");
        when(userRepository.findById(1L)).thenReturn(Optional.of(administrator(1L, "admin")));

        assertThatThrownBy(() -> service.updatePermissions(
                        1L, new com.jewellery.erp.user.dto.UpdateUserPermissionsRequest(Set.of(1L))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("change your own permissions");
    }
}
