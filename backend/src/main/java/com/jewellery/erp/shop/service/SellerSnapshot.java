package com.jewellery.erp.shop.service;

import com.jewellery.erp.shop.dto.ShopSettingsDto;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The shop's identity as it must appear on a document issued right now.
 *
 * <p>Invoices and purchase bills copy these values at the moment of issue. If the
 * shop later moves or re-registers, a reprint of an old bill must still show the
 * address and GSTIN it was actually issued under.
 */
public record SellerSnapshot(String name, String address, String mobile, String gstin) {

    public static SellerSnapshot of(ShopSettingsDto shop) {
        String cityAndPin = join(" - ", shop.city(), shop.pincode());
        return new SellerSnapshot(
                shop.shopName(),
                blankToNull(join(", ", shop.addressLine1(), shop.addressLine2(), cityAndPin)),
                blankToNull(join(", ", shop.mobileNumber(), shop.alternateMobileNumber())),
                shop.gstin());
    }

    private static String join(String separator, String... parts) {
        return Stream.of(parts)
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(separator));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
