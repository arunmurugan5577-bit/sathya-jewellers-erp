package com.jewellery.erp.hsn.mapper;

import com.jewellery.erp.common.dto.LookupDto;
import com.jewellery.erp.hsn.dto.HsnCodeDto;
import com.jewellery.erp.hsn.entity.HsnCode;
import org.springframework.stereotype.Component;

@Component
public class HsnCodeMapper {

    public HsnCodeDto toDto(HsnCode entity) {
        return new HsnCodeDto(
                entity.getId(),
                entity.getHsnCode(),
                entity.getDescription(),
                entity.getGstPercentage(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy());
    }

    /**
     * Dropdown label combines the code with its rate - "7113 (3%)" - because the
     * code alone does not tell the person picking it what tax it applies.
     */
    public LookupDto toLookup(HsnCode entity) {
        String label = "%s (%s%%)".formatted(entity.getHsnCode(), entity.getGstPercentage().stripTrailingZeros().toPlainString());
        return new LookupDto(entity.getId(), label, entity.getHsnCode());
    }
}
