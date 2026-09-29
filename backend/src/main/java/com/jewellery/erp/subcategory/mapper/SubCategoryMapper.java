package com.jewellery.erp.subcategory.mapper;

import com.jewellery.erp.common.dto.LookupDto;
import com.jewellery.erp.subcategory.dto.SubCategoryDto;
import com.jewellery.erp.subcategory.entity.SubCategory;
import org.springframework.stereotype.Component;

@Component
public class SubCategoryMapper {

    public SubCategoryDto toDto(SubCategory entity) {
        return new SubCategoryDto(
                entity.getId(),
                entity.getCategory().getId(),
                entity.getCategory().getName(),
                entity.getName(),
                entity.getCode(),
                entity.getDescription(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy());
    }

    public LookupDto toLookup(SubCategory entity) {
        return new LookupDto(entity.getId(), entity.getName(), entity.getCode());
    }
}
