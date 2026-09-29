package com.jewellery.erp.subcategory.repository;

import com.jewellery.erp.subcategory.entity.SubCategory;
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
public interface SubCategoryRepository
        extends JpaRepository<SubCategory, Long>, JpaSpecificationExecutor<SubCategory> {

    /**
     * Overridden purely to attach an entity graph: the list DTO shows the parent
     * category name, and without this every row would trigger its own query.
     */
    @Override
    @EntityGraph(attributePaths = {"category"})
    Page<SubCategory> findAll(Specification<SubCategory> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"category"})
    Optional<SubCategory> findById(Long id);

    /** Cascading dropdown source: active sub categories of one category. */
    List<SubCategory> findByCategoryIdAndActiveTrueOrderByNameAsc(Long categoryId);

    /**
     * Name uniqueness is scoped to the parent category: two categories may each
     * have a sub category with the same name.
     */
    @Query("select count(s) > 0 from SubCategory s "
            + "where s.category.id = :categoryId and lower(s.name) = lower(:name)")
    boolean existsByCategoryAndNameIgnoreCase(
            @Param("categoryId") Long categoryId, @Param("name") String name);

    @Query("select count(s) > 0 from SubCategory s "
            + "where s.category.id = :categoryId and lower(s.name) = lower(:name) and s.id <> :id")
    boolean existsByCategoryAndNameIgnoreCaseAndIdNot(
            @Param("categoryId") Long categoryId, @Param("name") String name, @Param("id") Long id);

    boolean existsByCodeIgnoreCase(String code);

    @Query("select count(s) > 0 from SubCategory s where lower(s.code) = lower(:code) and s.id <> :id")
    boolean existsByCodeIgnoreCaseAndIdNot(@Param("code") String code, @Param("id") Long id);

    @Query("select count(i) from InventoryItem i where i.subCategory.id = :id")
    long countInventoryItems(@Param("id") Long id);
}
