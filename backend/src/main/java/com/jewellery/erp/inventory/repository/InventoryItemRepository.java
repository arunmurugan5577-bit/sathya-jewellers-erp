package com.jewellery.erp.inventory.repository;

import com.jewellery.erp.inventory.entity.InventoryItem;
import com.jewellery.erp.inventory.entity.InventoryStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
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
public interface InventoryItemRepository
        extends JpaRepository<InventoryItem, Long>, JpaSpecificationExecutor<InventoryItem> {

    /**
     * The list screen shows the name of all five related masters. Without this
     * graph a twenty-row page would issue a hundred extra queries.
     */
    @Override
    @EntityGraph(attributePaths = {"itemType", "purity", "category", "subCategory", "hsnCode"})
    Page<InventoryItem> findAll(Specification<InventoryItem> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"itemType", "purity", "category", "subCategory", "hsnCode"})
    Optional<InventoryItem> findById(Long id);

    @EntityGraph(attributePaths = {"itemType", "purity", "category", "subCategory", "hsnCode"})
    Optional<InventoryItem> findBySerialNumber(String serialNumber);

    boolean existsBySerialNumber(String serialNumber);

    @Query("select count(i) > 0 from InventoryItem i where i.serialNumber = :serialNumber and i.id <> :id")
    boolean existsBySerialNumberAndIdNot(
            @Param("serialNumber") String serialNumber, @Param("id") Long id);

    long countByActiveTrue();

    long countByActiveTrueAndStatus(InventoryStatus status);

    List<InventoryItem> findAllBySerialNumberIn(Collection<String> serialNumbers);

    /**
     * Locks the given pieces for the rest of the transaction ({@code SELECT ... FOR UPDATE}).
     *
     * <p>This is what makes two simultaneous sales of the same piece safe: the
     * second transaction blocks here until the first commits, then re-reads the
     * row and finds it SOLD. Rows are locked in id order so two sales of
     * overlapping pieces cannot deadlock. No fetch joins - PostgreSQL rejects
     * FOR UPDATE on the nullable side of an outer join.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.id in :ids order by i.id")
    List<InventoryItem> lockAllById(@Param("ids") Collection<Long> ids);

    /** Invoice lines that reference the piece, cancelled invoices included. */
    @Query("select count(s) from SaleItem s where s.inventoryItem.id = :id")
    long countSaleLines(@Param("id") Long id);

    /**
     * Highest serial number currently issued, as a number.
     *
     * <p>The column is text so that leading zeros survive; the cast is what makes
     * "next serial" mean numerically-next rather than lexicographically-next.
     */
    @Query(value = "select coalesce(max(cast(serial_number as integer)), 0) from inventory_items",
            nativeQuery = true)
    int findHighestSerialNumber();

    /**
     * Total net weight of the stock still in the shop (active and unsold), in grams.
     *
     * <p>Returns null when there is no stock at all; the service substitutes zero.
     * Leaving the coalesce to Java rather than to JPQL keeps the return type
     * unambiguously BigDecimal whatever the dialect decides a literal 0 is.
     */
    @Query("select sum(i.weightGrams) from InventoryItem i where i.active = true and i.status = com.jewellery.erp.inventory.entity.InventoryStatus.AVAILABLE")
    BigDecimal sumActiveWeightGrams();
}
