package com.jewellery.erp.oldmetal.repository;

import com.jewellery.erp.oldmetal.entity.OldMetalStatus;
import com.jewellery.erp.oldmetal.entity.OldMetalTransaction;
import jakarta.persistence.LockModeType;
import java.util.Collection;
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
public interface OldMetalTransactionRepository
        extends JpaRepository<OldMetalTransaction, Long>, JpaSpecificationExecutor<OldMetalTransaction> {

    @EntityGraph(attributePaths = {"customer", "items", "items.itemType", "items.purity"})
    Optional<OldMetalTransaction> findDetailedById(Long id);

    /** Bills whose value this customer can still apply to a sale, lines included, in one query. */
    @Query("""
            select distinct t from OldMetalTransaction t
            left join fetch t.items i
            left join fetch i.itemType
            where t.customer.id = :customerId and t.status in :statuses
            order by t.transactionDate desc, t.id desc
            """)
    List<OldMetalTransaction> findUsableForCustomer(
            @Param("customerId") Long customerId, @Param("statuses") Collection<OldMetalStatus> statuses);

    /**
     * Locks bills for the current transaction, in id order. This is what stops two
     * sales spending the same old gold: the second waits here, then re-reads the
     * reduced balance.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from OldMetalTransaction t where t.id in :ids order by t.id")
    List<OldMetalTransaction> lockAllById(@Param("ids") Collection<Long> ids);
}
