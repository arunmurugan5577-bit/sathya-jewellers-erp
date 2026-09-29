package com.jewellery.erp.user.entity;

import com.jewellery.erp.common.entity.AuditableEntity;
import com.jewellery.erp.permission.entity.Permission;
import com.jewellery.erp.role.entity.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * An application user.
 *
 * <p>A user's effective permission set is the <em>union</em> of the permissions
 * carried by their roles and the permissions granted to them directly. The model
 * has no "deny" row on purpose: with {@code ROLE_USER} carrying nothing, direct
 * grants are already sufficient to express any staff access, and deny rules are
 * the classic source of unexplainable authorisation behaviour.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class User extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @Column(name = "username", nullable = false, length = 50)
    @ToString.Include
    private String username;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "mobile_number", length = 20)
    private String mobileNumber;

    /** BCrypt hash. Deliberately excluded from {@code toString} and every DTO. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "account_locked", nullable = false)
    private boolean accountLocked = false;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_permissions",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> directPermissions = new LinkedHashSet<>();

    // ------------------------------------------------------------ behaviour ---

    /** True when this user holds {@code ROLE_ADMIN}. */
    public boolean isAdministrator() {
        return roles.stream().anyMatch(role -> Role.ADMIN.equals(role.getName()));
    }

    /**
     * Effective permission codes: role permissions union direct grants.
     *
     * <p>Requires the {@code roles}, {@code roles.permissions} and
     * {@code directPermissions} associations to be initialised - repositories
     * expose an entity graph for exactly this.
     */
    public Set<String> effectivePermissionCodes() {
        return Stream.concat(
                        roles.stream().flatMap(role -> role.getPermissions().stream()),
                        directPermissions.stream())
                .map(Permission::getCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<String> roleNames() {
        return roles.stream().map(Role::getName).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** A user can authenticate only while active and unlocked. */
    public boolean canAuthenticate() {
        return active && !accountLocked;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof User user) || id == null) {
            return false;
        }
        return id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
