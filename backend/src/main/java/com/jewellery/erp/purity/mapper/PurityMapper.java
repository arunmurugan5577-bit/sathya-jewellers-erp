package com.jewellery.erp.purity.mapper;

import com.jewellery.erp.common.dto.LookupDto;
import com.jewellery.erp.purity.dto.PurityDto;
import com.jewellery.erp.purity.entity.Purity;
import org.springframework.stereotype.Component;

@Component
public class PurityMapper {

    public PurityDto toDto(Purity entity) {
        return new PurityDto(
                entity.getId(),
                entity.getItemType().getId(),
                entity.getItemType().getName(),
                entity.getName(),
                entity.getPurityValue(),
                entity.getDescription(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy());
    }

    /** Purity has no code column; the fineness value plays that role in dropdowns. */
    public LookupDto toLookup(Purity entity) {
        return new LookupDto(
                entity.getId(),
                entity.getName(),
                entity.getPurityValue().stripTrailingZeros().toPlainString());
    }
}
