package com.jewellery.erp.shop.service;

import com.jewellery.erp.common.exception.ResourceNotFoundException;
import com.jewellery.erp.common.util.StringNormalizer;
import com.jewellery.erp.shop.dto.ShopSettingsDto;
import com.jewellery.erp.shop.dto.ShopSettingsRequest;
import com.jewellery.erp.shop.entity.ShopSettings;
import com.jewellery.erp.shop.mapper.ShopSettingsMapper;
import com.jewellery.erp.shop.repository.ShopSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The shop profile.
 *
 * <p>Singleton by construction: the row is created by migration
 * {@code V3__seed_shop_settings.sql} and only ever updated, so both endpoints
 * are total - there is no state in which the settings screen has nothing to
 * show.
 *
 * <p>Blank strings are normalised to null before saving. The database check
 * constraints on GSTIN, pincode and e-mail only tolerate a well-formed value or
 * NULL, and an empty string is neither.
 */
@Service
@Transactional(readOnly = true)
public class ShopSettingsService {

    private static final Logger log = LoggerFactory.getLogger(ShopSettingsService.class);

    private final ShopSettingsRepository shopSettingsRepository;
    private final ShopSettingsMapper shopSettingsMapper;

    public ShopSettingsService(
            ShopSettingsRepository shopSettingsRepository, ShopSettingsMapper shopSettingsMapper) {
        this.shopSettingsRepository = shopSettingsRepository;
        this.shopSettingsMapper = shopSettingsMapper;
    }

    public ShopSettingsDto find() {
        return shopSettingsMapper.toDto(requireSettings());
    }

    @Transactional
    public ShopSettingsDto update(ShopSettingsRequest request) {
        ShopSettings entity = requireSettings();

        entity.setShopName(StringNormalizer.normalizeName(request.shopName()));
        entity.setAddressLine1(StringNormalizer.trimToNull(request.addressLine1()));
        entity.setAddressLine2(StringNormalizer.trimToNull(request.addressLine2()));
        entity.setCity(StringNormalizer.normalizeName(request.city()));
        entity.setState(StringNormalizer.normalizeName(request.state()));
        entity.setPincode(StringNormalizer.trimToNull(request.pincode()));
        entity.setMobileNumber(StringNormalizer.trimToNull(request.mobileNumber()));
        entity.setAlternateMobileNumber(StringNormalizer.trimToNull(request.alternateMobileNumber()));
        entity.setEmail(StringNormalizer.normalizeLower(request.email()));
        // GSTIN is upper case by definition of the format.
        entity.setGstin(StringNormalizer.normalizeCode(request.gstin()));

        log.info("Shop settings updated for '{}'", entity.getShopName());
        return shopSettingsMapper.toDto(entity);
    }

    /** The shop name, for invoice headers and the page title. */
    public String shopName() {
        return requireSettings().getShopName();
    }

    private ShopSettings requireSettings() {
        return shopSettingsRepository
                .findById(ShopSettings.SINGLETON_ID)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Shop settings are missing. Flyway migration V3 has not been applied."));
    }
}
