package com.jewellery.erp.wholesale.repository;

import com.jewellery.erp.wholesale.entity.WholesaleEstimate;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WholesaleEstimateRepository
        extends JpaRepository<WholesaleEstimate, Long>, JpaSpecificationExecutor<WholesaleEstimate> {

    @EntityGraph(attributePaths = {"items", "items.inventoryItem", "customer"})
    Optional<WholesaleEstimate> findWithItemsById(Long id);

    /** Locked for cancellation, so two people cannot reverse the same estimate twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from WholesaleEstimate e where e.id = :id")
    Optional<WholesaleEstimate> lockById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"customer"})
    Page<WholesaleEstimate> findAll(org.springframework.data.jpa.domain.Specification<WholesaleEstimate> spec,
            Pageable pageable);

    /** Everything in the window, for the report. Lines included: the report lists them. */
    @EntityGraph(attributePaths = {"items", "customer"})
    @Query("""
            select distinct e from WholesaleEstimate e
             where e.estimateDate between :from and :to
               and (:status is null or e.status = :status)
               and (:customerId is null or e.customer.id = :customerId)
             order by e.estimateDate, e.id""")
    List<WholesaleEstimate> findForReport(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("status") com.jewellery.erp.wholesale.entity.WholesaleStatus status,
            @Param("customerId") Long customerId);
}
