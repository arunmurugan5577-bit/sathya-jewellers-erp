package com.jewellery.erp.subcategory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jewellery.erp.category.entity.Category;
import com.jewellery.erp.category.service.CategoryService;
import com.jewellery.erp.common.exception.BusinessRuleException;
import com.jewellery.erp.common.exception.DuplicateResourceException;
import com.jewellery.erp.common.exception.ResourceNotFoundException;
import com.jewellery.erp.subcategory.dto.SubCategoryRequest;
import com.jewellery.erp.subcategory.entity.SubCategory;
import com.jewellery.erp.subcategory.mapper.SubCategoryMapper;
import com.jewellery.erp.subcategory.repository.SubCategoryRepository;
import com.jewellery.erp.subcategory.service.SubCategoryService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** A sub category always belongs to a category, and the backend proves it. */
@ExtendWith(MockitoExtension.class)
class SubCategoryServiceTest {

    @Mock private SubCategoryRepository subCategoryRepository;
    @Mock private CategoryService categoryService;

    private SubCategoryService service;

    private Category ring;

    @BeforeEach
    void setUp() {
        service = new SubCategoryService(subCategoryRepository, categoryService, new SubCategoryMapper());

        ring = new Category();
        ring.setId(10L);
        ring.setName("Ring");
        ring.setCode("RING");
    }

    private SubCategory mensRing() {
        SubCategory subCategory = new SubCategory();
        subCategory.setId(50L);
        subCategory.setName("Mens Ring");
        subCategory.setCode("RING-M");
        subCategory.setCategory(ring);
        subCategory.setActive(true);
        return subCategory;
    }

    @Test
    @DisplayName("a sub category cannot reference a category that does not exist")
    void rejectsUnknownCategory() {
        when(categoryService.requireActive(999L))
                .thenThrow(ResourceNotFoundException.of("Category", 999L));

        SubCategoryRequest request = new SubCategoryRequest(999L, "Mens Ring", "RING-M", null, null);

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(ResourceNotFoundException.class);
        verify(subCategoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("a sub category cannot be created under an inactive category")
    void rejectsInactiveCategory() {
        when(categoryService.requireActive(10L))
                .thenThrow(new BusinessRuleException("categoryId", "Category 'Ring' is inactive and cannot be used."));

        SubCategoryRequest request = new SubCategoryRequest(10L, "Mens Ring", "RING-M", null, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    @DisplayName("the same name may exist under two different categories")
    void allowsSameNameUnderDifferentCategories() {
        when(categoryService.requireActive(10L)).thenReturn(ring);
        when(subCategoryRepository.existsByCategoryAndNameIgnoreCase(10L, "Kids")).thenReturn(false);
        when(subCategoryRepository.existsByCodeIgnoreCase("RING-K")).thenReturn(false);
        when(subCategoryRepository.save(any(SubCategory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SubCategoryRequest request = new SubCategoryRequest(10L, "Kids", "RING-K", null, null);

        assertThat(service.create(request).categoryName()).isEqualTo("Ring");
    }

    @Test
    @DisplayName("the same name twice under one category is rejected")
    void rejectsDuplicateNameWithinCategory() {
        when(categoryService.requireActive(10L)).thenReturn(ring);
        when(subCategoryRepository.existsByCategoryAndNameIgnoreCase(10L, "Mens Ring")).thenReturn(true);

        SubCategoryRequest request = new SubCategoryRequest(10L, "Mens Ring", "RING-M", null, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already has a sub category named");

        verify(subCategoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("a sub category from another category is refused on an inventory item")
    void rejectsSubCategoryFromAnotherCategory() {
        when(subCategoryRepository.findById(50L)).thenReturn(Optional.of(mensRing()));

        assertThatThrownBy(() -> service.requireActiveInCategory(50L, 99L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("does not belong to the selected category");
    }

    @Test
    @DisplayName("an inactive sub category cannot be selected")
    void rejectsInactiveSubCategory() {
        SubCategory inactive = mensRing();
        inactive.setActive(false);
        when(subCategoryRepository.findById(50L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.requireActiveInCategory(50L, 10L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    @DisplayName("a sub category already used by stock cannot be moved to another category")
    void refusesReparentingWhenReferenced() {
        Category bangle = new Category();
        bangle.setId(11L);
        bangle.setName("Bangle");
        bangle.setCode("BANG");

        when(subCategoryRepository.findById(50L)).thenReturn(Optional.of(mensRing()));
        when(categoryService.requireActive(11L)).thenReturn(bangle);
        when(subCategoryRepository.countInventoryItems(50L)).thenReturn(3L);

        SubCategoryRequest moved = new SubCategoryRequest(11L, "Mens Ring", "RING-M", null, null);

        assertThatThrownBy(() -> service.update(50L, moved))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be moved to another category");
    }
}
