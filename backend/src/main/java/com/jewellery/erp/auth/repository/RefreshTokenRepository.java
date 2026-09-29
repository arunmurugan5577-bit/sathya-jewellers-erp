package com.jewellery.erp.auth.repository;

import com.jewellery.erp.auth.entity.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revokes every live session of a user. Called on logout-all, password reset,
     * deactivation and permission change so that those take effect immediately
     * rather than at the next access-token expiry.
     */
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now "
            + "where t.user.id = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    /** Housekeeping: drop tokens that expired more than a retention window ago. */
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
