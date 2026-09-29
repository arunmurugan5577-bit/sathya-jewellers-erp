package com.jewellery.erp.itemtype.repository;

import com.jewellery.erp.itemtype.entity.ItemType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemTypeRepository extends JpaRepository<ItemType, Long>, JpaSpecificationExecutor<ItemType> {

    /** Dropdown source: active records only, so inactive masters cannot be selected. */
    List<ItemType> findByActiveTrueOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByCodeIgnoreCase(String code);

    @Query("select count(t) > 0 from ItemType t where lower(t.name) = lower(:name) and t.id <> :id")
    boolean existsByNameIgnoreCaseAndIdNot(@Param("name") String name, @Param("id") Long id);

    @Query("select count(t) > 0 from ItemType t where lower(t.code) = lower(:code) and t.id <> :id")
    boolean existsByCodeIgnoreCaseAndIdNot(@Param("code") String code, @Param("id") Long id);

    @Query("select count(p) from Purity p where p.itemType.id = :id")
    long countPurities(@Param("id") Long id);

    @Query("select count(i) from InventoryItem i where i.itemType.id = :id")
    long countInventoryItems(@Param("id") Long id);
}
