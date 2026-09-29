package com.jewellery.erp.hsn.repository;

import com.jewellery.erp.hsn.entity.HsnCode;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface HsnCodeRepository extends JpaRepository<HsnCode, Long>, JpaSpecificationExecutor<HsnCode> {

    List<HsnCode> findByActiveTrueOrderByHsnCodeAsc();

    boolean existsByHsnCode(String hsnCode);

    @Query("select count(h) > 0 from HsnCode h where h.hsnCode = :hsnCode and h.id <> :id")
    boolean existsByHsnCodeAndIdNot(@Param("hsnCode") String hsnCode, @Param("id") Long id);

    @Query("select count(i) from InventoryItem i where i.hsnCode.id = :id")
    long countInventoryItems(@Param("id") Long id);
}
