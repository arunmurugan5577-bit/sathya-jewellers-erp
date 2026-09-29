package com.jewellery.erp.itemtype.mapper;

import com.jewellery.erp.common.dto.LookupDto;
import com.jewellery.erp.itemtype.dto.ItemTypeDto;
import com.jewellery.erp.itemtype.entity.ItemType;
import org.springframework.stereotype.Component;

@Component
public class ItemTypeMapper {

    public ItemTypeDto toDto(ItemType entity) {
        return toDto(entity, 0L);
    }

    public ItemTypeDto toDto(ItemType entity, long purityCount) {
        return new ItemTypeDto(
                entity.getId(),
                entity.getName(),
                entity.getCode(),
                entity.getDescription(),
                entity.isActive(),
                purityCount,
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy());
    }

    public LookupDto toLookup(ItemType entity) {
        return new LookupDto(entity.getId(), entity.getName(), entity.getCode());
    }
}
