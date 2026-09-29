package com.jewellery.erp.shop.mapper;

import com.jewellery.erp.shop.dto.ShopSettingsDto;
import com.jewellery.erp.shop.entity.ShopSettings;
import org.springframework.stereotype.Component;

@Component
public class ShopSettingsMapper {

    public ShopSettingsDto toDto(ShopSettings entity) {
        return new ShopSettingsDto(
                entity.getId(),
                entity.getShopName(),
                entity.getAddressLine1(),
                entity.getAddressLine2(),
                entity.getCity(),
                entity.getState(),
                entity.getPincode(),
                entity.getMobileNumber(),
                entity.getAlternateMobileNumber(),
                entity.getEmail(),
                entity.getGstin(),
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy());
    }
}
