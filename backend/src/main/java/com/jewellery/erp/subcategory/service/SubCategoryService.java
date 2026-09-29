package com.jewellery.erp.subcategory.service;

import com.jewellery.erp.category.entity.Category;
import com.jewellery.erp.category.service.CategoryService;
import com.jewellery.erp.common.dto.LookupDto;
import com.jewellery.erp.common.dto.MasterFilter;
import com.jewellery.erp.common.dto.PageResponse;
import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.DuplicateResourceException;
import com.jewellery.erp.common.exception.ReferencedRecordException;
import com.jewellery.erp.common.exception.ResourceNotFoundException;
import com.jewellery.erp.common.repository.NamedMasterSpecifications;
import com.jewellery.erp.common.util.StringNormalizer;
import com.jewellery.erp.subcategory.dto.SubCategoryDto;
import com.jewellery.erp.subcategory.dto.SubCategoryFilter;
import com.jewellery.erp.subcategory.dto.SubCategoryRequest;
import com.jewellery.erp.subcategory.entity.SubCategory;
import com.jewellery.erp.subcategory.mapper.SubCategoryMapper;
import com.jewellery.erp.subcategory.repository.SubCategoryRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sub category master.
 *
 * <p>Differs from the flat masters in two ways that matter:
 *
 * <ul>
 *   <li>The parent category is resolved through {@link CategoryService} rather
 *       than through the category repository. Modules talk to each other through
 *       services, never by reaching into another module's persistence layer -
 *       that is what keeps this a modular monolith instead of a large ball of
 *       JPA.
 *   <li>Name uniqueness is scoped to the parent, so "Kids" can exist under both
 *       Ring and Bangle, while the code stays unique shop-wide because it ends
 *       up on labels and reports.
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class SubCategoryService {

    private static final Logger log = LoggerFactory.getLogger(SubCategoryService.class);

    private final SubCategoryRepository subCategoryRepository;
    private final CategoryService categoryService;
    private final SubCategoryMapper subCategoryMapper;

    public SubCategoryService(
            SubCategoryRepository subCategoryRepository,
            CategoryService categoryService,
            SubCategoryMapper subCategoryMapper) {
        this.subCategoryRepository = subCategoryRepository;
        this.categoryService = categoryService;
        this.subCategoryMapper = subCategoryMapper;
    }

    // ------------------------------------------------------------- queries ---

    public PageResponse<SubCategoryDto> findAll(SubCategoryFilter filter, Pageable pageable) {
        Specification<SubCategory> specification =
                NamedMasterSpecifications.<SubCategory>matching(new MasterFilter(filter.search(), filter.active()));

        if (filter.categoryId() != null) {
            Long categoryId = filter.categoryId();
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("category").get("id"), categoryId));
        }

        return PageResponse.from(
                subCategoryRepository.findAll(specification, pageable), subCategoryMapper::toDto);
    }

    public SubCategoryDto findById(Long id) {
        return subCategoryMapper.toDto(requireSubCategory(id));
    }

    /**
     * Cascading dropdown source for the inventory form: the active sub categories
     * of one category.
     */
    public List<LookupDto> findActiveLookupByCategory(Long categoryId) {
        // Resolving the parent first turns an unknown id into a clear 404 rather
        // than a silently empty dropdown.
        categoryService.requireActive(categoryId);
        return subCategoryRepository.findByCategoryIdAndActiveTrueOrderByNameAsc(categoryId).stream()
                .map(subCategoryMapper::toLookup)
                .toList();
    }

    /**
     * Resolves a sub category and verifies it belongs to the expected category.
     *
     * <p>Used by the inventory service: the frontend cascade makes a mismatch
     * unlikely, but the API is callable without the frontend.
     */
    public SubCategory requireActiveInCategory(Long subCategoryId, Long categoryId) {
        SubCategory subCategory = requireSubCategory(subCategoryId);

        if (!subCategory.isActive()) {
            throw new BusinessRuleException(
                    "subCategoryId",
                    "Sub category '%s' is inactive and cannot be used.".formatted(subCategory.getName()));
        }
        if (!subCategory.getCategory().getId().equals(categoryId)) {
            throw new BusinessRuleException(
                    "subCategoryId",
                    "Sub category '%s' does not belong to the selected category."
                            .formatted(subCategory.getName()));
        }
        return subCategory;
    }

    // ------------------------------------------------------------ commands ---

    @Transactional
    public SubCategoryDto create(SubCategoryRequest request) {
        Category category = categoryService.requireActive(request.categoryId());
        String name = StringNormalizer.normalizeName(request.name());
        String code = StringNormalizer.normalizeCode(request.code());

        if (subCategoryRepository.existsByCategoryAndNameIgnoreCase(category.getId(), name)) {
            throw new DuplicateResourceException(
                    "name",
                    "Category '%s' already has a sub category named '%s'.".formatted(category.getName(), name));
        }
        if (subCategoryRepository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException(
                    "code", "Sub category code '%s' is already in use.".formatted(code));
        }

        SubCategory entity = new SubCategory();
        entity.setCategory(category);
        entity.setName(name);
        entity.setCode(code);
        entity.setDescription(StringNormalizer.trimToNull(request.description()));
        entity.setActive(request.active() == null || request.active());

        SubCategory saved = subCategoryRepository.save(entity);
        log.info("Sub category '{}' created under category '{}'", saved.getCode(), category.getCode());
        return subCategoryMapper.toDto(saved);
    }

    @Transactional
    public SubCategoryDto update(Long id, SubCategoryRequest request) {
        SubCategory entity = requireSubCategory(id);
        Category category = categoryService.requireActive(request.categoryId());
        String name = StringNormalizer.normalizeName(request.name());
        String code = StringNormalizer.normalizeCode(request.code());

        boolean movingToAnotherCategory = !entity.getCategory().getId().equals(category.getId());
        if (movingToAnotherCategory && subCategoryRepository.countInventoryItems(id) > 0) {
            // Re-parenting would silently rewrite the classification of inventory
            // that has already been recorded under the old category.
            throw new BusinessRuleException(
                    "categoryId",
                    "This sub category is already used by inventory items and cannot be moved to another category.");
        }

        if (subCategoryRepository.existsByCategoryAndNameIgnoreCaseAndIdNot(category.getId(), name, id)) {
            throw new DuplicateResourceException(
                    "name",
                    "Category '%s' already has a sub category named '%s'.".formatted(category.getName(), name));
        }
        if (subCategoryRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException(
                    "code", "Sub category code '%s' is already in use.".formatted(code));
        }

        entity.setCategory(category);
        entity.setName(name);
        entity.setCode(code);
        entity.setDescription(StringNormalizer.trimToNull(request.description()));
        if (request.active() != null) {
            entity.setActive(request.active());
        }

        log.info("Sub category '{}' updated", entity.getCode());
        return subCategoryMapper.toDto(entity);
    }

    @Transactional
    public SubCategoryDto updateStatus(Long id, boolean active) {
        SubCategory entity = requireSubCategory(id);
        entity.setActive(active);
        log.info("Sub category '{}' {}", entity.getCode(), active ? "activated" : "deactivated");
        return subCategoryMapper.toDto(entity);
    }

    @Transactional
    public void delete(Long id) {
        SubCategory entity = requireSubCategory(id);

        long items = subCategoryRepository.countInventoryItems(id);
        if (items > 0) {
            throw ReferencedRecordException.of(
                    "Sub category", entity.getName(), "%d inventory item(s)".formatted(items));
        }

        subCategoryRepository.delete(entity);
        log.info("Sub category '{}' deleted", entity.getCode());
    }

    // ------------------------------------------------------------- helpers ---

    private SubCategory requireSubCategory(Long id) {
        return subCategoryRepository
                .findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Sub category", id));
    }
}
