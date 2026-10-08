package com.jewellery.erp.inventory.mapper;

import com.jewellery.erp.inventory.dto.InventoryItemDto;
import com.jewellery.erp.inventory.entity.InventoryItem;
import org.springframework.stereotype.Component;

@Component
public class InventoryItemMapper {

    public InventoryItemDto toDto(InventoryItem entity) {
        return new InventoryItemDto(
                entity.getId(),
                entity.getSerialNumber(),
                entity.getItemType().getId(),
                entity.getItemType().getName(),
                entity.getPurity().getId(),
                entity.getPurity().getName(),
                entity.getPurity().getPurityValue(),
                entity.getCategory().getId(),
                entity.getCategory().getName(),
                // Sub category and HSN are optional, so every access is guarded.
                entity.getSubCategory() == null ? null : entity.getSubCategory().getId(),
                entity.getSubCategory() == null ? null : entity.getSubCategory().getName(),
                entity.getHsnCode() == null ? null : entity.getHsnCode().getId(),
                entity.getHsnCode() == null ? null : entity.getHsnCode().getHsnCode(),
                entity.getHsnCode() == null ? null : entity.getHsnCode().getGstPercentage(),
                entity.getSize(),
                entity.getWeightGrams(),
                entity.isBulk(),
                entity.getRemainingWeightGrams(),
                entity.getDescription(),
                entity.isActive(),
                entity.getStatus().name(),
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy());
    }
}
