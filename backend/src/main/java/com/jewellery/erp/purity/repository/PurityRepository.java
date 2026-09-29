package com.jewellery.erp.purity.repository;

import com.jewellery.erp.purity.entity.Purity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PurityRepository extends JpaRepository<Purity, Long>, JpaSpecificationExecutor<Purity> {

    /** Overridden to attach an entity graph - the list DTO shows the item type name. */
    @Override
    @EntityGraph(attributePaths = {"itemType"})
    Page<Purity> findAll(Specification<Purity> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"itemType"})
    Optional<Purity> findById(Long id);

    /** Cascading dropdown source: active purities of one item type. */
    List<Purity> findByItemTypeIdAndActiveTrueOrderByPurityValueDesc(Long itemTypeId);

    @Query("select count(p) > 0 from Purity p "
            + "where p.itemType.id = :itemTypeId and lower(p.name) = lower(:name)")
    boolean existsByItemTypeAndNameIgnoreCase(
            @Param("itemTypeId") Long itemTypeId, @Param("name") String name);

    @Query("select count(p) > 0 from Purity p "
            + "where p.itemType.id = :itemTypeId and lower(p.name) = lower(:name) and p.id <> :id")
    boolean existsByItemTypeAndNameIgnoreCaseAndIdNot(
            @Param("itemTypeId") Long itemTypeId, @Param("name") String name, @Param("id") Long id);

    @Query("select count(p) > 0 from Purity p "
            + "where p.itemType.id = :itemTypeId and p.purityValue = :value")
    boolean existsByItemTypeAndValue(
            @Param("itemTypeId") Long itemTypeId, @Param("value") BigDecimal value);

    @Query("select count(p) > 0 from Purity p "
            + "where p.itemType.id = :itemTypeId and p.purityValue = :value and p.id <> :id")
    boolean existsByItemTypeAndValueAndIdNot(
            @Param("itemTypeId") Long itemTypeId, @Param("value") BigDecimal value, @Param("id") Long id);

    @Query("select count(i) from InventoryItem i where i.purity.id = :id")
    long countInventoryItems(@Param("id") Long id);
}
