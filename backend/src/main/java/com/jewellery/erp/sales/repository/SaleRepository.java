package com.jewellery.erp.sales.repository;

import com.jewellery.erp.sales.entity.Sale;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long>, JpaSpecificationExecutor<Sale> {

    /** The invoice with its lines and their masters; adjustments and payments load lazily. */
    @EntityGraph(attributePaths = {
        "customer", "items", "items.itemType", "items.purity", "items.category", "items.subCategory"
    })
    Optional<Sale> findDetailedById(Long id);

    /** Serialises payment, cancellation and remarks changes on one invoice. */
    /**
     * Every invoice of a run, with the lines each one prints.
     *
     * <p>The same graph {@link #findDetailedById} uses, so one query covers a
     * batch that would otherwise be one round trip per invoice. Cancelled ones
     * are left out here rather than filtered afterwards: they are not part of
     * what the shop is printing, and fetching them only to drop them would
     * load their lines too.
     */
    @EntityGraph(attributePaths = {
        "customer", "items", "items.itemType", "items.purity", "items.category", "items.subCategory"
    })
    @Query("""
            select distinct s from Sale s
            where s.status <> com.jewellery.erp.sales.entity.SaleStatus.CANCELLED
              and s.invoiceDate >= :from and s.invoiceDate <= :to
            order by s.invoiceNumber
            """)
    List<Sale> findForPrinting(
            @Param("from") LocalDate from, @Param("to") LocalDate to, org.springframework.data.domain.Limit limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> lockById(@Param("id") Long id);
}
