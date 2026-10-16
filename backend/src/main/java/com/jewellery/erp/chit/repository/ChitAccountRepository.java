package com.jewellery.erp.chit.repository;

import com.jewellery.erp.chit.entity.ChitAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChitAccountRepository
        extends JpaRepository<ChitAccount, Long>, JpaSpecificationExecutor<ChitAccount> {

    /** The list screen shows the customer and the scheme on every row. */
    @Override
    @EntityGraph(attributePaths = {"customer", "scheme"})
    Page<ChitAccount> findAll(Specification<ChitAccount> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "scheme", "payments"})
    Optional<ChitAccount> findDetailedById(Long id);

    /**
     * Locks the chit for the rest of the transaction.
     *
     * <p>What makes two counters taking the same month's instalment safe: the
     * second waits here, then finds the instalment already recorded. No fetch
     * joins - PostgreSQL rejects FOR UPDATE on the nullable side of an outer
     * join.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChitAccount c where c.id = :id")
    Optional<ChitAccount> lockById(@Param("id") Long id);
}
