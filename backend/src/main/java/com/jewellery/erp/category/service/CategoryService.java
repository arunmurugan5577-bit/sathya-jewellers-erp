package com.jewellery.erp.category.service;

import com.jewellery.erp.category.dto.CategoryDto;
import com.jewellery.erp.category.dto.CategoryRequest;
import com.jewellery.erp.category.entity.Category;
import com.jewellery.erp.category.mapper.CategoryMapper;
import com.jewellery.erp.category.repository.CategoryRepository;
import com.jewellery.erp.common.dto.LookupDto;
import com.jewellery.erp.common.dto.MasterFilter;
import com.jewellery.erp.common.dto.PageResponse;
import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.DuplicateResourceException;
import com.jewellery.erp.common.exception.ReferencedRecordException;
import com.jewellery.erp.common.exception.ResourceNotFoundException;
import com.jewellery.erp.common.repository.NamedMasterSpecifications;
import com.jewellery.erp.common.util.StringNormalizer;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Category master - follows the pattern documented on {@code ItemTypeService}. */
@Service
@Transactional(readOnly = true)
public class CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryRepository categoryRepository, CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
    }

    // ------------------------------------------------------------- queries ---

    public PageResponse<CategoryDto> findAll(MasterFilter filter, Pageable pageable) {
        return PageResponse.from(
                categoryRepository.findAll(NamedMasterSpecifications.matching(filter), pageable),
                categoryMapper::toDto);
    }

    public CategoryDto findById(Long id) {
        return categoryMapper.toDto(requireCategory(id), categoryRepository.countSubCategories(id));
    }

    public List<LookupDto> findActiveLookup() {
        return categoryRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(categoryMapper::toLookup)
                .toList();
    }

    /** Dashboard metric. */
    public long countActive() {
        return categoryRepository.countByActiveTrue();
    }

    /** Resolves a reference for another module; the record must exist and be active. */
    public Category requireActive(Long id) {
        Category category = requireCategory(id);
        if (!category.isActive()) {
            throw new BusinessRuleException(
                    "categoryId", "Category '%s' is inactive and cannot be used.".formatted(category.getName()));
        }
        return category;
    }

    // ------------------------------------------------------------ commands ---

    @Transactional
    public CategoryDto create(CategoryRequest request) {
        String name = StringNormalizer.normalizeName(request.name());
        String code = StringNormalizer.normalizeCode(request.code());

        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("name", "A category named '%s' already exists.".formatted(name));
        }
        if (categoryRepository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("code", "Category code '%s' is already in use.".formatted(code));
        }

        Category entity = new Category();
        entity.setName(name);
        entity.setCode(code);
        entity.setDescription(StringNormalizer.trimToNull(request.description()));
        entity.setActive(request.active() == null || request.active());

        Category saved = categoryRepository.save(entity);
        log.info("Category '{}' created", saved.getCode());
        return categoryMapper.toDto(saved);
    }

    @Transactional
    public CategoryDto update(Long id, CategoryRequest request) {
        Category entity = requireCategory(id);
        String name = StringNormalizer.normalizeName(request.name());
        String code = StringNormalizer.normalizeCode(request.code());

        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new DuplicateResourceException("name", "A category named '%s' already exists.".formatted(name));
        }
        if (categoryRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateResourceException("code", "Category code '%s' is already in use.".formatted(code));
        }

        entity.setName(name);
        entity.setCode(code);
        entity.setDescription(StringNormalizer.trimToNull(request.description()));
        if (request.active() != null) {
            entity.setActive(request.active());
        }

        log.info("Category '{}' updated", entity.getCode());
        return categoryMapper.toDto(entity, categoryRepository.countSubCategories(id));
    }

    @Transactional
    public CategoryDto updateStatus(Long id, boolean active) {
        Category entity = requireCategory(id);
        entity.setActive(active);
        log.info("Category '{}' {}", entity.getCode(), active ? "activated" : "deactivated");
        return categoryMapper.toDto(entity, categoryRepository.countSubCategories(id));
    }

    @Transactional
    public void delete(Long id) {
        Category entity = requireCategory(id);

        long subCategories = categoryRepository.countSubCategories(id);
        if (subCategories > 0) {
            throw ReferencedRecordException.of(
                    "Category", entity.getName(), "%d sub category record(s)".formatted(subCategories));
        }
        long items = categoryRepository.countInventoryItems(id);
        if (items > 0) {
            throw ReferencedRecordException.of(
                    "Category", entity.getName(), "%d inventory item(s)".formatted(items));
        }

        categoryRepository.delete(entity);
        log.info("Category '{}' deleted", entity.getCode());
    }

    // ------------------------------------------------------------- helpers ---

    private Category requireCategory(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Category", id));
    }
}
