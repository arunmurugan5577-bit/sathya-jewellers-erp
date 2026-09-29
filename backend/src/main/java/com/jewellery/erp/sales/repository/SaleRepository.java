package com.jewellery.erp.sales.repository;

import com.jewellery.erp.sales.entity.Sale;
import jakarta.persistence.LockModeType;
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
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> lockById(@Param("id") Long id);
}
