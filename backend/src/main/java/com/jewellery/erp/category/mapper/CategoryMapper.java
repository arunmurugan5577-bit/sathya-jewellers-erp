package com.jewellery.erp.category.mapper;

import com.jewellery.erp.category.dto.CategoryDto;
import com.jewellery.erp.category.entity.Category;
import com.jewellery.erp.common.dto.LookupDto;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryDto toDto(Category entity) {
        return toDto(entity, 0L);
    }

    public CategoryDto toDto(Category entity, long subCategoryCount) {
        return new CategoryDto(
                entity.getId(),
                entity.getName(),
                entity.getCode(),
                entity.getDescription(),
                entity.isActive(),
                subCategoryCount,
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy());
    }

    public LookupDto toLookup(Category entity) {
        return new LookupDto(entity.getId(), entity.getName(), entity.getCode());
    }
}
