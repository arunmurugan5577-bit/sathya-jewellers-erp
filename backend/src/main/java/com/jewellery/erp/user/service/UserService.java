package com.jewellery.erp.user.service;

import com.jewellery.erp.auth.service.RefreshTokenService;
import com.jewellery.erp.common.dto.PageResponse;
import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.DuplicateResourceException;
import com.jewellery.erp.common.exception.ResourceNotFoundException;
import com.jewellery.erp.common.util.StringNormalizer;
import com.jewellery.erp.permission.entity.Permission;
import com.jewellery.erp.permission.repository.PermissionRepository;
import com.jewellery.erp.permission.service.PermissionService;
import com.jewellery.erp.role.entity.Role;
import com.jewellery.erp.role.repository.RoleRepository;
import com.jewellery.erp.security.SecurityUtils;
import com.jewellery.erp.user.dto.ChangePasswordRequest;
import com.jewellery.erp.user.dto.CreateUserRequest;
import com.jewellery.erp.user.dto.ResetPasswordRequest;
import com.jewellery.erp.user.dto.UpdateUserPermissionsRequest;
import com.jewellery.erp.user.dto.UpdateUserRequest;
import com.jewellery.erp.user.dto.UserDto;
import com.jewellery.erp.user.dto.UserFilter;
import com.jewellery.erp.user.dto.UserPermissionsDto;
import com.jewellery.erp.user.entity.User;
import com.jewellery.erp.user.mapper.UserMapper;
import com.jewellery.erp.user.repository.UserRepository;
import com.jewellery.erp.user.repository.UserSpecifications;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User administration: accounts, status, passwords and direct permission grants.
 *
 * <p>Three rules are enforced here rather than in the UI, because the UI is not
 * a security boundary:
 *
 * <ul>
 *   <li>An administrator cannot deactivate or strip the permissions of their own
 *       account - the quickest way to lock an installation out of itself.
 *   <li>The last active administrator cannot be deactivated or demoted.
 *   <li>Any change that narrows access - deactivation, password reset,
 *       permission change - revokes the affected user's refresh tokens so it
 *       takes effect within the access-token lifetime rather than at the end of
 *       a multi-day session.
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionService permissionService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            PermissionService permissionService,
            RefreshTokenService refreshTokenService,
            PasswordEncoder passwordEncoder,
            UserMapper userMapper) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.permissionService = permissionService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    // ------------------------------------------------------------- queries ---

    public PageResponse<UserDto> findAll(UserFilter filter, Pageable pageable) {
        return PageResponse.from(
                userRepository.findAll(UserSpecifications.matching(filter), pageable), userMapper::toDto);
    }

    public UserDto findById(Long id) {
        return userMapper.toDto(requireUser(id));
    }

    /** The full permission matrix for one user, ready for the permission screen. */
    public UserPermissionsDto findPermissions(Long id) {
        User user = userRepository
                .findWithAuthoritiesById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));

        Set<String> rolePermissionCodes = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<Long> directPermissionIds =
                user.getDirectPermissions().stream().map(Permission::getId).collect(Collectors.toSet());

        return new UserPermissionsDto(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.roleNames().stream().sorted().toList(),
                user.isAdministrator(),
                permissionService.findGroupedByModule(),
                directPermissionIds,
                rolePermissionCodes);
    }

    // ------------------------------------------------------------ commands ---

    @Transactional
    public UserDto create(CreateUserRequest request) {
        String username = StringNormalizer.trimToNull(request.username());
        String email = StringNormalizer.normalizeLower(request.email());

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new DuplicateResourceException(
                    "username", "Username '%s' is already taken.".formatted(username));
        }
        if (email != null && userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException(
                    "email", "E-mail '%s' is already registered.".formatted(email));
        }

        User user = new User();
        user.setUsername(username);
        user.setFullName(StringNormalizer.normalizeName(request.fullName()));
        user.setEmail(email);
        user.setMobileNumber(StringNormalizer.trimToNull(request.mobileNumber()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setActive(true);
        user.setAccountLocked(false);
        user.setMustChangePassword(Boolean.TRUE.equals(request.mustChangePassword()));
        user.setRoles(resolveRoles(request.roleIds()));
        user.setDirectPermissions(resolvePermissions(request.permissionIds()));

        User saved = userRepository.save(user);
        log.info("User '{}' created by '{}'", saved.getUsername(),
                SecurityUtils.currentUsername().orElse(SecurityUtils.SYSTEM_USER));
        return userMapper.toDto(saved);
    }

    @Transactional
    public UserDto update(Long id, UpdateUserRequest request) {
        User user = requireUser(id);
        String email = StringNormalizer.normalizeLower(request.email());

        if (email != null && userRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new DuplicateResourceException(
                    "email", "E-mail '%s' is already registered.".formatted(email));
        }

        user.setFullName(StringNormalizer.normalizeName(request.fullName()));
        user.setEmail(email);
        user.setMobileNumber(StringNormalizer.trimToNull(request.mobileNumber()));

        if (request.roleIds() != null) {
            Set<Role> roles = resolveRoles(request.roleIds());
            boolean losingAdmin = user.isAdministrator()
                    && roles.stream().noneMatch(role -> Role.ADMIN.equals(role.getName()));
            if (losingAdmin) {
                denySelfLockout(user, "remove your own administrator role");
                requireAnotherAdministratorExists(user);
            }
            user.setRoles(roles);
        }

        log.info("User '{}' updated by '{}'", user.getUsername(),
                SecurityUtils.currentUsername().orElse(SecurityUtils.SYSTEM_USER));
        return userMapper.toDto(user);
    }

    /** Activates or deactivates an account. Deactivating also ends live sessions. */
    @Transactional
    public UserDto updateStatus(Long id, boolean active) {
        User user = requireUser(id);
        if (user.isActive() == active) {
            return userMapper.toDto(user);
        }

        if (!active) {
            denySelfLockout(user, "deactivate your own account");
            if (user.isAdministrator()) {
                requireAnotherAdministratorExists(user);
            }
        }

        user.setActive(active);
        if (!active) {
            refreshTokenService.revokeAllForUser(user.getId());
        }

        log.info("User '{}' {} by '{}'", user.getUsername(), active ? "activated" : "deactivated",
                SecurityUtils.currentUsername().orElse(SecurityUtils.SYSTEM_USER));
        return userMapper.toDto(user);
    }

    /** Administrator password reset. Ends every session the user had open. */
    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest request) {
        User user = requireUser(id);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(Boolean.TRUE.equals(request.mustChangePassword()));
        user.setAccountLocked(false);
        refreshTokenService.revokeAllForUser(user.getId());

        // The new password is never logged, in any form.
        log.info("Password reset for user '{}' by '{}'", user.getUsername(),
                SecurityUtils.currentUsername().orElse(SecurityUtils.SYSTEM_USER));
    }

    /** Self-service password change; the current password must be presented. */
    @Transactional
    public void changeOwnPassword(ChangePasswordRequest request) {
        Long userId = SecurityUtils.currentUserId()
                .orElseThrow(() -> new BadCredentialsException("No authenticated user."));
        User user = requireUser(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("currentPassword", "The current password is incorrect.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException(
                    "newPassword", "The new password must be different from the current one.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        refreshTokenService.revokeAllForUser(user.getId());
        log.info("User '{}' changed their password", user.getUsername());
    }

    /** Replaces the user's direct permission grants. */
    @Transactional
    public UserPermissionsDto updatePermissions(Long id, UpdateUserPermissionsRequest request) {
        User user = requireUser(id);
        denySelfLockout(user, "change your own permissions");

        Set<Permission> permissions = resolvePermissions(request.permissionIds());
        user.setDirectPermissions(permissions);

        // Force the next call to pick up the new set instead of waiting for the
        // current access token to expire.
        refreshTokenService.revokeAllForUser(user.getId());

        log.info("Permissions of user '{}' set to {} direct grant(s) by '{}'",
                user.getUsername(), permissions.size(),
                SecurityUtils.currentUsername().orElse(SecurityUtils.SYSTEM_USER));
        return findPermissions(id);
    }

    /**
     * Hard deletes an account.
     *
     * <p>Only ever permitted for an account that has never signed in: once a user
     * has acted in the system their username appears in audit columns across the
     * database, and removing the row would orphan that history. Everything else
     * is a deactivation.
     */
    @Transactional
    public void delete(Long id) {
        User user = requireUser(id);
        denySelfLockout(user, "delete your own account");

        if (user.getLastLoginAt() != null) {
            throw new BusinessRuleException(
                    "This user has already signed in and appears in audit history. Deactivate the account instead.");
        }
        if (user.isAdministrator()) {
            requireAnotherAdministratorExists(user);
        }

        refreshTokenService.revokeAllForUser(user.getId());
        userRepository.delete(user);
        log.info("User '{}' deleted by '{}'", user.getUsername(),
                SecurityUtils.currentUsername().orElse(SecurityUtils.SYSTEM_USER));
    }

    // ------------------------------------------------------------- helpers ---

    private User requireUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }

    /**
     * Resolves role ids, defaulting to {@code ROLE_USER}. An unknown id is an
     * error rather than a silent omission - a half-applied role assignment is
     * worse than a rejected request.
     */
    private Set<Role> resolveRoles(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new LinkedHashSet<>(List.of(roleRepository
                    .findByName(Role.USER)
                    .orElseThrow(() -> new IllegalStateException(
                            "ROLE_USER is missing. The seed migration has not been applied."))));
        }
        List<Role> roles = roleRepository.findAllById(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new BusinessRuleException("roleIds", "One or more selected roles no longer exist.");
        }
        return new LinkedHashSet<>(roles);
    }

    private Set<Permission> resolvePermissions(Set<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        List<Permission> permissions = permissionRepository.findAllByIdIn(permissionIds);
        if (permissions.size() != permissionIds.size()) {
            throw new BusinessRuleException(
                    "permissionIds", "One or more selected permissions no longer exist.");
        }
        return new LinkedHashSet<>(permissions);
    }

    /** Refuses an operation that would strip the signed-in administrator's own access. */
    private void denySelfLockout(User target, String action) {
        boolean self = SecurityUtils.currentUserId()
                .map(currentId -> currentId.equals(target.getId()))
                .orElse(false);
        if (self) {
            throw new BusinessRuleException("You cannot %s.".formatted(action));
        }
    }

    /** Refuses an operation that would leave the installation with no administrator. */
    private void requireAnotherAdministratorExists(User target) {
        if (userRepository.countOtherActiveAdministrators(target.getId()) == 0) {
            throw new BusinessRuleException(
                    "This is the last active administrator. Create or activate another administrator first.");
        }
    }
}
