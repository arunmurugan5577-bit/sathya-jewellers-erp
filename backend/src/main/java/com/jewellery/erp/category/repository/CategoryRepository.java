package com.jewellery.erp.category.repository;

import com.jewellery.erp.category.entity.Category;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long>, JpaSpecificationExecutor<Category> {

    List<Category> findByActiveTrueOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByCodeIgnoreCase(String code);

    @Query("select count(c) > 0 from Category c where lower(c.name) = lower(:name) and c.id <> :id")
    boolean existsByNameIgnoreCaseAndIdNot(@Param("name") String name, @Param("id") Long id);

    @Query("select count(c) > 0 from Category c where lower(c.code) = lower(:code) and c.id <> :id")
    boolean existsByCodeIgnoreCaseAndIdNot(@Param("code") String code, @Param("id") Long id);

    @Query("select count(s) from SubCategory s where s.category.id = :id")
    long countSubCategories(@Param("id") Long id);

    @Query("select count(i) from InventoryItem i where i.category.id = :id")
    long countInventoryItems(@Param("id") Long id);

    long countByActiveTrue();
}
