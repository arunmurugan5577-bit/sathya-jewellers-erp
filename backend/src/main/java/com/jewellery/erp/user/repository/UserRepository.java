package com.jewellery.erp.user.repository;

import com.jewellery.erp.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    /**
     * Loads a user with everything needed to build the security principal.
     *
     * <p>The associations are Sets, so fetching both collections in one graph is
     * safe (no {@code MultipleBagFetchException}); the duplicate rows the join
     * produces are de-duplicated by the Set semantics.
     */
    @EntityGraph(attributePaths = {"roles", "roles.permissions", "directPermissions"})
    Optional<User> findWithAuthoritiesByUsernameIgnoreCase(String username);

    @EntityGraph(attributePaths = {"roles", "roles.permissions", "directPermissions"})
    Optional<User> findWithAuthoritiesById(Long id);

    @EntityGraph(attributePaths = {"roles", "directPermissions"})
    Optional<User> findWithRolesById(Long id);

    Optional<User> findByUsernameIgnoreCase(String username);

    /**
     * Whether the account is still switched on, without loading the user.
     *
     * <p>Read on every authenticated request so that deactivating someone takes
     * effect at once rather than whenever their access token happens to expire.
     */
    @Query("select u.active from User u where u.id = :id")
    Optional<Boolean> findActiveById(@Param("id") Long id);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select count(u) > 0 from User u where lower(u.username) = lower(:username) and u.id <> :id")
    boolean existsByUsernameIgnoreCaseAndIdNot(@Param("username") String username, @Param("id") Long id);

    @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email) and u.id <> :id")
    boolean existsByEmailIgnoreCaseAndIdNot(@Param("email") String email, @Param("id") Long id);

    long countByActiveTrue();

    /** True when at least one enabled administrator exists - drives the bootstrap. */
    @Query("select count(u) > 0 from User u join u.roles r where r.name = 'ROLE_ADMIN' and u.active = true")
    boolean existsActiveAdministrator();

    /**
     * Active administrators other than the given user. Guards the "do not remove
     * the last administrator" rule without loading the user table.
     */
    @Query("select count(distinct u) from User u join u.roles r "
            + "where r.name = 'ROLE_ADMIN' and u.active = true and u.id <> :excludedUserId")
    long countOtherActiveAdministrators(@Param("excludedUserId") Long excludedUserId);
}
